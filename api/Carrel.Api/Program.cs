using System.Net.Http.Headers;
using System.Security.Claims;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.RateLimiting;
using Carrel.Api.Accounts;
using Carrel.Api.Books;
using Carrel.Api.Data;
using Carrel.Api.Imports;
using Carrel.Api.Library;
using Carrel.Api.Profiles;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.EntityFrameworkCore;
using Npgsql;

var builder = WebApplication.CreateBuilder(args);

var supabaseUrl = builder.Configuration["Supabase:Url"]
    ?? throw new InvalidOperationException("Supabase:Url is not configured.");

// Add services to the container.
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();
builder.Services.AddHealthChecks();
// Verify the database server's certificate against Supabase's root CA, not just encrypt the connection.
var connectionString = new NpgsqlConnectionStringBuilder(builder.Configuration.GetConnectionString("Carrel"))
{
    SslMode = SslMode.VerifyFull,
    RootCertificate = Path.Combine(AppContext.BaseDirectory, "supabase-ca.crt"),
}.ConnectionString;
builder.Services.AddDbContext<CarrelDbContext>(options => options
    .UseNpgsql(connectionString)
    .UseSnakeCaseNamingConvention());

// Import batches go through Cloud Tasks when a queue is configured (production), and run in-process otherwise.
var importQueue = builder.Configuration.GetSection("Imports:Queue").Get<ImportQueueOptions>() ?? new ImportQueueOptions();

var authentication = builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        // Supabase Auth publishes OpenID discovery and its public signing keys under /auth/v1.
        options.Authority = $"{supabaseUrl}/auth/v1";
        options.Audience = "authenticated";
        options.MapInboundClaims = false;
    });
if (importQueue.UsesCloudTasks)
{
    // Cloud Tasks signs each batch request with a Google identity token for the API's service account, with the
    // API's address as the audience. Only the batch endpoint's policy uses this scheme.
    authentication.AddJwtBearer(ImportEndpoints.CloudTasksScheme, options =>
    {
        options.Authority = "https://accounts.google.com";
        options.Audience = importQueue.ServiceUrl;
        options.MapInboundClaims = false;
        options.TokenValidationParameters.ValidIssuers = ["https://accounts.google.com", "accounts.google.com"];
    });
}

// Every endpoint requires a signed-in user unless it opts out with AllowAnonymous().
builder.Services.AddAuthorizationBuilder()
    .SetFallbackPolicy(new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build())
    .AddPolicy(ImportEndpoints.CloudTasksPolicy, policy => policy
        .AddAuthenticationSchemes(ImportEndpoints.CloudTasksScheme)
        // Google only issues tokens for a service account's email to callers allowed to act as it.
        .RequireAssertion(context => context.User.FindFirstValue("email") is { } email && email == importQueue.ServiceAccount));

builder.Services.AddCors(options => options.AddDefaultPolicy(policy => policy
    .WithOrigins(builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? [])
    .AllowAnyHeader()
    .AllowAnyMethod()
    // Lets the website read how long a rate-limited visitor should wait.
    .WithExposedHeaders("Retry-After")));

// Cloud Run puts the visitor's address last in X-Forwarded-For; earlier entries come from the client and can be faked,
// so only the last one is used (ForwardLimit 1). Signed-out visitors are rate limited by this address.
builder.Services.Configure<ForwardedHeadersOptions>(options =>
{
    options.ForwardedHeaders = ForwardedHeaders.XForwardedFor;
    options.ForwardLimit = 1;
    options.KnownIPNetworks.Clear();
    options.KnownProxies.Clear();
});

// Limits per signed-in user, or per address for signed-out visitors (who get lower ones): a general one for every
// endpoint, and a tighter one where external book sources may be called. Signed-out book lookups also share one daily
// allowance, so anonymous traffic can't use up Hardcover's daily request quota and break search for readers.
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    // Say how long to wait, so the website can tell a one-minute limit from the daily signed-out allowance.
    options.OnRejected = (context, _) =>
    {
        if (context.Lease.TryGetMetadata(MetadataName.RetryAfter, out var retryAfter))
        {
            context.HttpContext.Response.Headers.RetryAfter = ((int)Math.Ceiling(retryAfter.TotalSeconds)).ToString();
        }
        return ValueTask.CompletedTask;
    };
    options.GlobalLimiter = PartitionedRateLimiter.CreateChained(
        PartitionedRateLimiter.Create<HttpContext, string>(context =>
            RateLimitPartition.GetFixedWindowLimiter(RateLimitPartitionKey(context), _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = IsSignedIn(context) ? 120 : 60,
                Window = TimeSpan.FromMinutes(1),
            })),
        PartitionedRateLimiter.Create<HttpContext, string>(context =>
            !IsSignedIn(context) && IsBookSourceRequest(context)
                ? RateLimitPartition.GetFixedWindowLimiter("anonymous-book-sources", _ => new FixedWindowRateLimiterOptions
                {
                    PermitLimit = 2000,
                    Window = TimeSpan.FromDays(1),
                })
                : RateLimitPartition.GetNoLimiter("unlimited")));
    options.AddPolicy(BookEndpoints.BookSourcesRateLimit, context =>
        RateLimitPartition.GetFixedWindowLimiter(RateLimitPartitionKey(context), _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = IsSignedIn(context) ? 30 : 15,
            Window = TimeSpan.FromMinutes(1),
        }));
    // Each profile loads a whole library, and anyone can ask for one.
    options.AddPolicy(ProfileEndpoints.ReadersRateLimit, context =>
        RateLimitPartition.GetFixedWindowLimiter(RateLimitPartitionKey(context), _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = IsSignedIn(context) ? 30 : 15,
            Window = TimeSpan.FromMinutes(1),
        }));
    options.AddPolicy(AccountEndpoints.AccountRateLimit, context =>
        RateLimitPartition.GetFixedWindowLimiter(RateLimitPartitionKey(context),
            _ => new FixedWindowRateLimiterOptions { PermitLimit = 5, Window = TimeSpan.FromMinutes(1) }));
});

builder.Services.ConfigureHttpJsonOptions(options =>
    options.SerializerOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower)));

// Search results are cached in memory; the size limit counts entries.
builder.Services.AddMemoryCache(options => options.SizeLimit = 1000);

var contactEmail = builder.Configuration["BookSources:ContactEmail"];
var userAgent = contactEmail is null ? "Carrel/0.1" : $"Carrel/0.1 ({contactEmail})";

builder.Services.AddHttpClient<HardcoverClient>(client =>
{
    client.BaseAddress = new Uri("https://api.hardcover.app/v1/graphql");
    client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer",
        builder.Configuration["Hardcover:ApiToken"] ?? throw new InvalidOperationException("Hardcover:ApiToken is not configured."));
    client.DefaultRequestHeaders.UserAgent.ParseAdd(userAgent);
});
builder.Services.AddHttpClient<OpenLibraryClient>(client =>
{
    client.BaseAddress = new Uri("https://openlibrary.org/");
    client.DefaultRequestHeaders.UserAgent.ParseAdd(userAgent);
    client.Timeout = TimeSpan.FromSeconds(15);
});
builder.Services.AddHttpClient<SupabaseAuthClient>(client =>
{
    client.BaseAddress = new Uri($"{supabaseUrl}/auth/v1/");
    client.Timeout = TimeSpan.FromSeconds(15);
});
builder.Services.AddScoped<CoverSuppression>();
builder.Services.AddScoped<BookService>();
builder.Services.AddScoped<SeriesService>();
builder.Services.AddScoped<LibraryService>();
builder.Services.AddScoped<LibraryExport>();
builder.Services.AddScoped<RecommendationService>();
builder.Services.AddSingleton(importQueue);
if (importQueue.UsesCloudTasks)
{
    builder.Services.AddSingleton<IImportQueue, CloudTasksImportQueue>();
}
else
{
    builder.Services.AddSingleton<IImportQueue, InProcessImportQueue>();
}
builder.Services.AddScoped<ImportProcessor>();
builder.Services.AddScoped<ImportService>();

var app = builder.Build();

// Apply pending migrations before taking requests. Cloud Run only sends traffic to a new revision once it has started,
// so a failed migration stops the deploy and the old revision keeps serving. The old revision also runs against the
// new schema until the switch, so migrations must stay additive (see the README).
using (var scope = app.Services.CreateScope())
{
    await scope.ServiceProvider.GetRequiredService<CarrelDbContext>().Database.MigrateAsync();
}

// Configure the HTTP request pipeline.
app.UseForwardedHeaders();

// The API only returns JSON: nothing in a response should run, be framed, or be sniffed as another type.
app.Use((context, next) =>
{
    var headers = context.Response.Headers;
    headers["Content-Security-Policy"] = "default-src 'none'; frame-ancestors 'none'";
    headers["X-Content-Type-Options"] = "nosniff";
    headers["X-Frame-Options"] = "DENY";
    headers["Referrer-Policy"] = "no-referrer";
    headers["Strict-Transport-Security"] = "max-age=31536000";
    return next(context);
});

app.UseCors();
app.UseAuthentication();
app.UseAuthorization();
app.UseRateLimiter();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi().AllowAnonymous();
}

app.MapHealthChecks("/health").AllowAnonymous();

app.MapGet("/me", (ClaimsPrincipal user) => new
{
    Id = user.FindFirstValue("sub"),
    Email = user.FindFirstValue("email"),
});

app.MapBookEndpoints();
app.MapLibraryEndpoints();
app.MapProfileEndpoints();
app.MapAccountEndpoints();
app.MapImportEndpoints(importQueue.UsesCloudTasks);

app.Run();

static bool IsSignedIn(HttpContext context) => context.User.FindFirstValue("sub") is not null;

static string RateLimitPartitionKey(HttpContext context) =>
    context.User.FindFirstValue("sub") is { } userId ? $"user:{userId}" : $"ip:{context.Connection.RemoteIpAddress}";

static bool IsBookSourceRequest(HttpContext context) =>
    context.Request.Path.StartsWithSegments("/books") || context.Request.Path.StartsWithSegments("/series");
