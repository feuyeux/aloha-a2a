/**
 * Main entry point for the Aloha Server.
 */

import dotenv from 'dotenv';
import { AlohaServer } from './agent.js';
import { getLogger, initLogFile } from './logger.js';

const logger = getLogger('server.index');

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
    const transportMode = (process.env.TRANSPORT_MODE || 'rest').toLowerCase();

    // Initialize log file output
    initLogFile(transportMode);

    logger.info(`Server transport: ${transportMode.toUpperCase()}`);

    // Create and start server
    const server = new AlohaServer(grpcPort, jsonrpcPort, restPort, host, transportMode);

    try {
        await server.start();
    } catch (error) {
        logger.error(`Failed to start server: ${error}`);
        process.exit(1);
    }
}

// Handle graceful shutdown
process.on('SIGINT', () => {
    logger.info('Shutdown signal received, stopping Dice Agent...');
    logger.info('Dice Agent stopped');
    process.exit(0);
});

process.on('SIGTERM', () => {
    logger.info('Shutdown signal received, stopping Dice Agent...');
    logger.info('Dice Agent stopped');
    process.exit(0);
});

// Start the agent
main().catch((error) => {
    logger.error(`Fatal error: ${error}`);
    process.exit(1);
});
