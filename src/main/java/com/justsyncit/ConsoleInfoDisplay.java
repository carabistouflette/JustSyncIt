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

/**
 * Console implementation of ApplicationInfoDisplay.
 * Follows Single Responsibility Principle by focusing only on console output.
 */
public class ConsoleInfoDisplay implements ApplicationInfoDisplay {

    @Override
    public void displayBlake3Info(Blake3Service blake3Service) {
        Blake3Service.Blake3Info info = blake3Service.getInfo();
        
        System.out.println("\n=== BLAKE3 Implementation Information ===");
        System.out.println("Version: " + info.getVersion());
        System.out.println("SIMD Support: " + (info.hasSimdSupport() ? "Yes" : "No"));
        System.out.println("Instruction Set: " + info.getSimdInstructionSet());
        System.out.println("JNI Implementation: " + (info.isJniImplementation() ? "Yes" : "No"));
        System.out.println("=====================================\n");
    }
}