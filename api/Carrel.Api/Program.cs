using System.Net.Http.Headers;
using System.Security.Claims;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.RateLimiting;
using Carrel.Api.Accounts;
using Carrel.Api.Books;
using Carrel.Api.Data;
using Carrel.Api.Library;
using Carrel.Api.Profiles;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

var supabaseUrl = builder.Configuration["Supabase:Url"]
    ?? throw new InvalidOperationException("Supabase:Url is not configured.");

// Add services to the container.
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();
builder.Services.AddHealthChecks();
builder.Services.AddDbContext<CarrelDbContext>(options => options
    .UseNpgsql(builder.Configuration.GetConnectionString("Carrel"))
    .UseSnakeCaseNamingConvention());

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        // Supabase Auth publishes OpenID discovery and its public signing keys under /auth/v1.
        options.Authority = $"{supabaseUrl}/auth/v1";
        options.Audience = "authenticated";
        options.MapInboundClaims = false;
    });

// Every endpoint requires a signed-in user unless it opts out with AllowAnonymous().
builder.Services.AddAuthorizationBuilder()
    .SetFallbackPolicy(new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build());

builder.Services.AddCors(options => options.AddDefaultPolicy(policy => policy
    .WithOrigins(builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? [])
    .AllowAnyHeader()
    .AllowAnyMethod()));

// Limits per signed-in user: a general one for every endpoint, and a tighter one where external book sources may be called.
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(context =>
        RateLimitPartition.GetFixedWindowLimiter(RateLimitPartitionKey(context),
            _ => new FixedWindowRateLimiterOptions { PermitLimit = 120, Window = TimeSpan.FromMinutes(1) }));
    options.AddPolicy(BookEndpoints.BookSourcesRateLimit, context =>
        RateLimitPartition.GetFixedWindowLimiter(RateLimitPartitionKey(context),
            _ => new FixedWindowRateLimiterOptions { PermitLimit = 30, Window = TimeSpan.FromMinutes(1) }));
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

var app = builder.Build();

// Configure the HTTP request pipeline.
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

app.Run();

static string RateLimitPartitionKey(HttpContext context) => context.User.FindFirstValue("sub") ?? "anonymous";
