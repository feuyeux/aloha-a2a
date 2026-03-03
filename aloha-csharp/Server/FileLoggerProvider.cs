using Microsoft.Extensions.Logging;

/// <summary>
/// Simple file-based logger provider that writes log entries to a file
/// in the unified format: TIMESTAMP - COMPONENT - LEVEL - message
/// </summary>
public sealed class FileLoggerProvider : ILoggerProvider
{
    private readonly StreamWriter _writer;
    private readonly object _lock = new();

    public FileLoggerProvider(string filePath)
    {
        _writer = new StreamWriter(filePath, append: true) { AutoFlush = true };
    }

    public ILogger CreateLogger(string categoryName)
    {
        return new FileLogger(categoryName, _writer, _lock);
    }

    public void Dispose()
    {
        _writer.Dispose();
    }

    private sealed class FileLogger(string category, StreamWriter writer, object @lock) : ILogger
    {
        public IDisposable? BeginScope<TState>(TState state) where TState : notnull => null;

        public bool IsEnabled(LogLevel logLevel) => logLevel >= LogLevel.Information;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
        {
            if (!IsEnabled(logLevel)) return;

            var level = logLevel switch
            {
                LogLevel.Trace => "TRACE",
                LogLevel.Debug => "DEBUG",
                LogLevel.Information => "INFO",
                LogLevel.Warning => "WARN",
                LogLevel.Error => "ERROR",
                LogLevel.Critical => "FATAL",
                _ => "INFO"
            };

            var timestamp = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss,fff");
            var message = formatter(state, exception);
            var line = $"{timestamp} - {category} - {level} - {message}";

            if (exception != null)
            {
                line += Environment.NewLine + exception;
            }

            lock (@lock)
            {
                writer.WriteLine(line);
            }
        }
    }
}
