using Google.Api.Gax.ResourceNames;
using Google.Cloud.Tasks.V2;
using Google.Protobuf.WellKnownTypes;
using Grpc.Core;
using Task = System.Threading.Tasks.Task;

namespace Carrel.Api.Imports;

/// <summary>Runs an import's next batch, by asking Cloud Tasks (production) or in this process (local development).</summary>
public interface IImportQueue
{
    /// <summary>Queues batch <paramref name="batchNumber"/>; queueing the same batch twice runs it once.</summary>
    Task EnqueueAsync(long importId, int batchNumber, TimeSpan delay, CancellationToken ct);
}

/// <summary>Where Cloud Tasks sends batches, from configuration (Imports:Queue). No queue name means run in-process.</summary>
public class ImportQueueOptions
{
    public string? Project { get; set; }
    public string? Location { get; set; }
    public string? Name { get; set; }

    /// <summary>The API's own address; tasks call it, and it's the audience of their identity tokens.</summary>
    public string? ServiceUrl { get; set; }

    /// <summary>The service account tasks are signed as; the batch endpoint only accepts tokens for it.</summary>
    public string? ServiceAccount { get; set; }

    public bool UsesCloudTasks => !string.IsNullOrEmpty(Name);
}

/// <summary>
/// Cloud Run only gives the API CPU while it's handling a request, so each batch arrives as a request from Cloud Tasks,
/// signed with a Google identity token for the API's service account. Cloud Tasks retries failed batches with backoff.
/// </summary>
public class CloudTasksImportQueue(ImportQueueOptions options, ILogger<CloudTasksImportQueue> logger) : IImportQueue
{
    private readonly Lazy<CloudTasksClient> client = new(CloudTasksClient.Create);

    public async Task EnqueueAsync(long importId, int batchNumber, TimeSpan delay, CancellationToken ct)
    {
        var queue = new QueueName(options.Project, options.Location, options.Name);
        var task = new Google.Cloud.Tasks.V2.Task
        {
            // Task names are unique per queue, so a batch queued twice (a retried batch, say) only runs once.
            TaskName = new TaskName(options.Project, options.Location, options.Name, $"import-{importId}-{batchNumber}"),
            ScheduleTime = Timestamp.FromDateTimeOffset(DateTimeOffset.UtcNow + delay),
            HttpRequest = new Google.Cloud.Tasks.V2.HttpRequest
            {
                HttpMethod = Google.Cloud.Tasks.V2.HttpMethod.Post,
                Url = $"{options.ServiceUrl}/internal/imports/{importId}/process",
                OidcToken = new OidcToken { ServiceAccountEmail = options.ServiceAccount, Audience = options.ServiceUrl },
            },
        };
        try
        {
            await client.Value.CreateTaskAsync(queue.ToString(), task, ct);
        }
        catch (RpcException e) when (e.StatusCode == StatusCode.AlreadyExists)
        {
            logger.LogInformation("Batch {Batch} of import {ImportId} was already queued.", batchNumber, importId);
        }
    }
}

/// <summary>Local development: no Cloud Tasks, and the machine keeps its CPU, so batches run in the background here.</summary>
public class InProcessImportQueue(IServiceScopeFactory scopes, ILogger<InProcessImportQueue> logger) : IImportQueue
{
    public Task EnqueueAsync(long importId, int batchNumber, TimeSpan delay, CancellationToken ct)
    {
        _ = Task.Run(async () =>
        {
            try
            {
                await Task.Delay(delay);
                // Like Cloud Tasks, try again later while another batch holds the import.
                for (var attempt = 0; attempt < 5; attempt++)
                {
                    using var scope = scopes.CreateScope();
                    if (await scope.ServiceProvider.GetRequiredService<ImportProcessor>().ProcessAsync(importId, CancellationToken.None))
                    {
                        return;
                    }
                    await Task.Delay(TimeSpan.FromMinutes(1));
                }
            }
            catch (Exception e)
            {
                logger.LogError(e, "Batch {Batch} of import {ImportId} failed.", batchNumber, importId);
            }
        });
        return Task.CompletedTask;
    }
}
