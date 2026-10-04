# Build
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY Carrel.Api/Carrel.Api.csproj Carrel.Api/
RUN dotnet restore Carrel.Api/Carrel.Api.csproj -r linux-x64 -p:PublishReadyToRun=true
COPY Carrel.Api/ Carrel.Api/
RUN dotnet publish Carrel.Api/Carrel.Api.csproj -c Release -r linux-x64 --self-contained false -o /app --no-restore -p:PublishReadyToRun=true

# Run
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app .
USER $APP_UID
ENTRYPOINT ["dotnet", "Carrel.Api.dll"]
