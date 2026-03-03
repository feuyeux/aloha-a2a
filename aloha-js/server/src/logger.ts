/**
 * Unified logger for consistent log format across all implementations.
 * Format: TIMESTAMP - COMPONENT - LEVEL - message
 * Outputs to both console and log file under aloha-log/
 */

import * as fs from 'fs';
import * as path from 'path';

type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

/** Resolved log directory (defaults to D:\coding\aloha-a2a\aloha-log) */
const LOG_DIR = process.env.ALOHA_LOG_DIR || path.resolve(__dirname, '..', '..', '..', 'aloha-log');

let logStream: fs.WriteStream | null = null;

/**
 * Initialize file logging for the JS/TS server.
 * Call once at startup with the transport mode.
 */
export function initLogFile(transport: string): void {
    try {
        fs.mkdirSync(LOG_DIR, { recursive: true });
        const logPath = path.join(LOG_DIR, `js-server-${transport}.log`);
        logStream = fs.createWriteStream(logPath, { flags: 'a' });
        const ts = formatTimestamp();
        logStream.write(`${ts} - server.index - INFO - Log file: ${logPath}\n`);
    } catch (err) {
        console.error(`WARNING: failed to open log file: ${err}`);
    }
}

function formatTimestamp(): string {
    const now = new Date();
    const pad = (n: number, w: number = 2) => String(n).padStart(w, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())},${pad(now.getMilliseconds(), 3)}`;
}

class Logger {
    private component: string;

    constructor(component: string) {
        this.component = component;
    }

    private log(level: LogLevel, message: string): void {
        const timestamp = formatTimestamp();
        const formatted = `${timestamp} - ${this.component} - ${level} - ${message}`;
        switch (level) {
            case 'ERROR':
                console.error(formatted);
                break;
            case 'WARN':
                console.warn(formatted);
                break;
            default:
                console.log(formatted);
                break;
        }
        // Also write to log file
        if (logStream) {
            logStream.write(formatted + '\n');
        }
    }

    debug(message: string): void {
        this.log('DEBUG', message);
    }

    info(message: string): void {
        this.log('INFO', message);
    }

    warn(message: string): void {
        this.log('WARN', message);
    }

    error(message: string): void {
        this.log('ERROR', message);
    }
}

export function getLogger(component: string): Logger {
    return new Logger(component);
}
