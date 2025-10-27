/**
 * Main entry point for the A2A Host client.
 */

import { Command } from 'commander';
import dotenv from 'dotenv';
import { Client } from './client.js';

// Load environment variables
dotenv.config();

/**
 * Main function to run the host client.
 */
async function main() {
    const program = new Command();

    program
        .name('aloha-js-host')
        .description('A2A Host client with multi-transport support')
        .version('1.0.0')
        .option('-t, --transport <type>', 'Transport protocol to use (jsonrpc, grpc, rest)', 'rest')
        .option('-h, --host <hostname>', 'Agent hostname', 'localhost')
        .option('-p, --port <port>', 'Agent port (default: 14000 for gRPC, 14001 for JSON-RPC, 14002 for REST)')
        .option('-m, --message <text>', 'Message to send')
        .option('-c, --context <id>', 'Context ID for conversation continuity')
        .parse(process.argv);

    const options = program.opts();

    // Construct server URL based on transport
    let serverUrl: string;
    const transport = options.transport.toLowerCase();
    
    // Set default port based on transport if not specified
    let port = options.port;
    if (!port) {
        if (transport === 'grpc') {
            port = '14000';
        } else if (transport === 'jsonrpc') {
            port = '14001';
        } else if (transport === 'rest') {
            port = '14002';
        }
    }

    if (transport === 'rest') {
        serverUrl = `http://${options.host}:${port}`;
    } else if (transport === 'grpc') {
        serverUrl = `${options.host}:${port}`;
        console.warn('Note: gRPC transport support in @a2a-js/sdk may be limited');
    } else if (transport === 'jsonrpc') {
        serverUrl = `ws://${options.host}:${port}`;
        console.warn('Note: JSON-RPC transport support in @a2a-js/sdk may be limited');
    } else {
        console.error(`Unsupported transport: ${transport}`);
        console.error('Supported transports: jsonrpc, grpc, rest');
        process.exit(1);
    }

    console.log('='.repeat(60));
    console.log('A2A Host Client');
    console.log('='.repeat(60));
    console.log(`Transport: ${transport}`);
    console.log(`Server URL: ${serverUrl}`);
    console.log('='.repeat(60));
    console.log('');

    try {
        // Create and initialize client
        const client = new Client(serverUrl, transport);
        await client.initialize();

        console.log('');

        // Check if message was provided
        if (options.message) {
            // Send single message
            console.log('Sending message...');
            const response = await client.sendMessage(options.message, options.context);

            console.log('');
            console.log('='.repeat(60));
            console.log('Agent Response:');
            console.log('='.repeat(60));
            console.log(response);
            console.log('='.repeat(60));

            await client.close();
        } else {
            // Interactive mode
            console.log('Interactive mode not yet implemented.');
            console.log('Please use the --message option to send a message.');
            console.log('');
            console.log('Example:');
            console.log(`  node dist/index.js --transport rest --host localhost --port 14002 --message "Roll a 6-sided dice"`);

            await client.close();
            process.exit(0);
        }
    } catch (error) {
        console.error('');
        console.error('Error:', error);
        process.exit(1);
    }
}

// Handle graceful shutdown
process.on('SIGINT', () => {
    console.log('\nReceived interrupt signal, shutting down...');
    process.exit(0);
});

process.on('SIGTERM', () => {
    console.log('\nReceived termination signal, shutting down...');
    process.exit(0);
});

// Start the client
main().catch((error) => {
    console.error('Fatal error:', error);
    process.exit(1);
});

