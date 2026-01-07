package com.justsyncit.modules;

import com.justsyncit.ApplicationInfoDisplay;
import com.justsyncit.ConsoleInfoDisplay;
import com.justsyncit.ServiceException;
import com.justsyncit.command.BackupCommand;
import com.justsyncit.command.CommandRegistry;
import com.justsyncit.command.DedupStatsCommand;
import com.justsyncit.command.DiffCommand;
import com.justsyncit.command.HashCommand;
import com.justsyncit.command.IntegrityCommand;
import com.justsyncit.command.RestoreCommand;
import com.justsyncit.command.VerifyCommand;
import com.justsyncit.network.command.NetworkCommand;
import com.justsyncit.hash.Blake3Service;
import com.justsyncit.network.NetworkService;
import com.justsyncit.storage.ContentStore;
import com.justsyncit.storage.metadata.MetadataService;
import com.justsyncit.backup.BackupService;
import com.justsyncit.restore.RestoreService;
import com.justsyncit.integrity.service.IntegrityCheckService;

import java.io.IOException;

/**
 * Module responsible for creating and registering commands.
 */
public class CommandModule {

    public ApplicationInfoDisplay createInfoDisplay() {
        return new ConsoleInfoDisplay();
    }

    public CommandRegistry createCommandRegistry(
            Blake3Service blake3Service,
            MetadataService metadataService,
            IntegrityCheckService integrityService) {

        CommandRegistry registry = new CommandRegistry();
        ApplicationInfoDisplay console = createInfoDisplay();

        // Register core commands
        registry.register(new HashCommand(blake3Service, console));
        registry.register(new VerifyCommand());
        registry.register(new DedupStatsCommand());
        registry.register(new DiffCommand(metadataService));
        registry.register(new IntegrityCommand(integrityService));

        return registry;
    }

    public CommandRegistry createCommandRegistryWithNetwork(
            Blake3Service blake3Service,
            NetworkService networkService,
            ContentStore contentStore,
            MetadataService metadataService,
            IntegrityCheckService integrityService) throws ServiceException {

        CommandRegistry registry = createCommandRegistry(blake3Service, metadataService, integrityService);

        try {
            registry.register(NetworkCommand.create(networkService, contentStore));
        } catch (RuntimeException e) {
            throw new ServiceException("Unexpected error registering network command", e);
        }

        return registry;
    }

    public BackupCommand createBackupCommand(BackupService backupService, NetworkService networkService)
            throws ServiceException {
        try {
            return new BackupCommand(backupService, networkService);
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create backup command", e);
        }
    }

    public RestoreCommand createRestoreCommand(RestoreService restoreService, NetworkService networkService)
            throws ServiceException {
        try {
            return new RestoreCommand(restoreService, networkService);
        } catch (RuntimeException e) {
            throw new ServiceException("Failed to create restore command", e);
        }
    }
}
