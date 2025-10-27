namespace Aloha.A2A.Agent;

/// <summary>
/// Dice Agent that provides dice rolling and prime checking capabilities
/// </summary>
public class DiceAgent
{
    private readonly ILogger<DiceAgent> _logger;
    private readonly IConfiguration _configuration;

    public DiceAgent(ILogger<DiceAgent> logger, IConfiguration configuration)
    {
        _logger = logger;
        _configuration = configuration;
    }

    /// <summary>
    /// Gets the agent card describing capabilities
    /// </summary>
    public AgentCard GetAgentCard()
    {
        var agentName = _configuration["Agent:Name"] ?? "Dice Agent";
        var agentDescription = _configuration["Agent:Description"] 
            ?? "An agent that can roll arbitrary dice and check prime numbers";
        var agentVersion = _configuration["Agent:Version"] ?? "1.0.0";
        
        var jsonRpcPort = _configuration.GetValue<int>("Ports:JsonRpc", 11000);
        var grpcPort = _configuration.GetValue<int>("Ports:Grpc", 11001);
        var restPort = _configuration.GetValue<int>("Ports:Rest", 11002);

        return new AgentCard
        {
            Name = agentName,
            Description = agentDescription,
            Url = $"localhost:{jsonRpcPort}",
            Version = agentVersion,
            Capabilities = new AgentCapabilities
            {
                Streaming = true,
                PushNotifications = false
            },
            DefaultInputModes = new List<string> { "text" },
            DefaultOutputModes = new List<string> { "text" },
            Skills = new List<Skill>
            {
                new Skill
                {
                    Id = "roll-dice",
                    Name = "Roll Dice",
                    Description = "Rolls an N-sided dice",
                    Tags = new List<string> { "dice", "random" },
                    Examples = new List<string> { "Roll a 20-sided dice", "Roll a 6-sided dice" }
                },
                new Skill
                {
                    Id = "check-prime",
                    Name = "Prime Checker",
                    Description = "Checks if numbers are prime",
                    Tags = new List<string> { "math", "prime" },
                    Examples = new List<string> 
                    { 
                        "Is 17 prime?", 
                        "Check if 2, 4, 7, 9, 11 are prime" 
                    }
                }
            },
            PreferredTransport = "grpc",
            Transports = new List<TransportInfo>
            {
                new TransportInfo
                {
                    Type = "jsonrpc",
                    Url = $"ws://localhost:{jsonRpcPort}",
                    Port = jsonRpcPort
                },
                new TransportInfo
                {
                    Type = "grpc",
                    Url = $"localhost:{grpcPort}",
                    Port = grpcPort
                },
                new TransportInfo
                {
                    Type = "rest",
                    Url = $"http://localhost:{restPort}",
                    Port = restPort
                }
            }
        };
    }
}

/// <summary>
/// Agent card model
/// </summary>
public class AgentCard
{
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
    public AgentCapabilities Capabilities { get; set; } = new();
    public List<string> DefaultInputModes { get; set; } = new();
    public List<string> DefaultOutputModes { get; set; } = new();
    public List<Skill> Skills { get; set; } = new();
    public string PreferredTransport { get; set; } = string.Empty;
    public List<TransportInfo> Transports { get; set; } = new();
}

public class AgentCapabilities
{
    public bool Streaming { get; set; }
    public bool PushNotifications { get; set; }
}

public class Skill
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public List<string> Tags { get; set; } = new();
    public List<string> Examples { get; set; } = new();
}

public class TransportInfo
{
    public string Type { get; set; } = string.Empty;
    public string Url { get; set; } = string.Empty;
    public int Port { get; set; }
}
