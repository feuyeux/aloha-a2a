/**
 * Main entry point for the Dice Agent.
 */

import dotenv from 'dotenv';
import { DiceAgent } from './agent.js';

// Load environment variables
dotenv.config();

/**
 * Main function to start the agent.
 */
async function main() {
    // Get configuration from environment
    const grpcPort = parseInt(process.env.GRPC_PORT || '14000', 10);
    const jsonrpcPort = parseInt(process.env.JSONRPC_PORT || '14001', 10);
    const restPort = parseInt(process.env.REST_PORT || '14002', 10);
    const host = process.env.HOST || '0.0.0.0';

    // Create and start agent
    const agent = new DiceAgent(grpcPort, jsonrpcPort, restPort, host);

    try {
        await agent.start();

        console.log('Agent started successfully. Press Ctrl+C to stop.');
    } catch (error) {
        console.error('Failed to start agent:', error);
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

// Start the agent
main().catch((error) => {
    console.error('Fatal error:', error);
    process.exit(1);
});
