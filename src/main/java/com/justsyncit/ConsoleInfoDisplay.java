/*
 * JustSyncIt - Backup solution
 * Copyright (C) 2023 JustSyncIt Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.justsyncit;

import com.justsyncit.hash.Blake3Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Console implementation of ApplicationInfoDisplay.
 * Follows Single Responsibility Principle by focusing only on console output.
 */
public class ConsoleInfoDisplay implements ApplicationInfoDisplay {

    /** Logger for console info display operations. */
    private static final Logger logger = LoggerFactory.getLogger(ConsoleInfoDisplay.class);

    @Override
    public void displayBlake3Info(Blake3Service blake3Service) {
        Blake3Service.Blake3Info info = blake3Service.getInfo();

        logger.info("\n=== BLAKE3 Implementation Information ===");
        logger.info("Version: {}", info.getVersion());
        logger.info("SIMD Support: {}", info.hasSimdSupport() ? "Yes" : "No");
        logger.info("Instruction Set: {}", info.getSimdInstructionSet());
        logger.info("JNI Implementation: {}", info.isJniImplementation() ? "Yes" : "No");
        logger.info("=====================================\n");
    }
}