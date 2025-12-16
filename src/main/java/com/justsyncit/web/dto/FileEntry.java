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

package com.justsyncit.web.dto;

import java.time.Instant;

/**
 * DTO representing a file or directory entry for the file browser.
 */
public final class FileEntry {

    private String name;
    private String path;
    private boolean isDirectory;
    private long size;
    private Instant modifiedAt;
    private String permissions;
    private boolean isHidden;
    private boolean isSymlink;

    public FileEntry() {
        // Default constructor for JSON serialization
    }

    public static FileEntry directory(String name, String path, Instant modifiedAt) {
        FileEntry entry = new FileEntry();
        entry.setName(name);
        entry.setPath(path);
        entry.setDirectory(true);
        entry.setModifiedAt(modifiedAt);
        return entry;
    }

    public static FileEntry file(String name, String path, long size, Instant modifiedAt) {
        FileEntry entry = new FileEntry();
        entry.setName(name);
        entry.setPath(path);
        entry.setDirectory(false);
        entry.setSize(size);
        entry.setModifiedAt(modifiedAt);
        return entry;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public void setHidden(boolean hidden) {
        isHidden = hidden;
    }

    public boolean isSymlink() {
        return isSymlink;
    }

    public void setSymlink(boolean symlink) {
        isSymlink = symlink;
    }
}
