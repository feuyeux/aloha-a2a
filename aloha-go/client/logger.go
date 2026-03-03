package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"runtime"
)

// logFile holds the open log file handle (if any) so all loggers share the same file.
var logFile *os.File

// InitLogFile sets up file-based logging for the Go client.
// It writes to D:\coding\aloha-a2a\aloha-log\go-client-{transport}.log (on Windows)
// Output goes to both stderr and the log file.
func InitLogFile(transport string) {
	logDir := resolveLogDir()
	if logDir == "" {
		return
	}
	_ = os.MkdirAll(logDir, 0o755)

	filename := filepath.Join(logDir, fmt.Sprintf("go-client-%s.log", transport))
	f, err := os.OpenFile(filename, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		log.Printf("WARNING: failed to open log file %s: %v", filename, err)
		return
	}
	logFile = f
	log.SetOutput(io.MultiWriter(os.Stderr, f))
	log.Printf("client - INFO - Log file: %s", filename)
}

// resolveLogDir returns the aloha-log directory path.
func resolveLogDir() string {
	if dir := os.Getenv("ALOHA_LOG_DIR"); dir != "" {
		return dir
	}
	if runtime.GOOS == "windows" {
		return `D:\coding\aloha-a2a\aloha-log`
	}
	return filepath.Join("..", "..", "aloha-log")
}

// Logger provides leveled logging with a component name.
// Format: TIMESTAMP - COMPONENT - LEVEL - message
// (TIMESTAMP is provided by Go's standard log package)
type Logger struct {
	component string
}

// NewLogger creates a new Logger for the given component.
func NewLogger(component string) *Logger {
	return &Logger{component: component}
}

func (l *Logger) format(level, msg string) string {
	return fmt.Sprintf("%s - %s - %s", l.component, level, msg)
}

// Debug logs a DEBUG level message.
func (l *Logger) Debug(format string, args ...interface{}) {
	log.Printf(l.format("DEBUG", fmt.Sprintf(format, args...)))
}

// Info logs an INFO level message.
func (l *Logger) Info(format string, args ...interface{}) {
	log.Printf(l.format("INFO", fmt.Sprintf(format, args...)))
}

// Warn logs a WARN level message.
func (l *Logger) Warn(format string, args ...interface{}) {
	log.Printf(l.format("WARN", fmt.Sprintf(format, args...)))
}

// Error logs an ERROR level message.
func (l *Logger) Error(format string, args ...interface{}) {
	log.Printf(l.format("ERROR", fmt.Sprintf(format, args...)))
}

// Fatal logs an ERROR level message and exits.
func (l *Logger) Fatal(format string, args ...interface{}) {
	log.Fatalf(l.format("ERROR", fmt.Sprintf(format, args...)))
}

// Println logs an INFO level message.
func (l *Logger) Println(msg string) {
	log.Printf(l.format("INFO", msg))
}
