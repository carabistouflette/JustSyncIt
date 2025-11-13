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

package com.justsyncit.simd;

/**
 * Immutable implementation of SimdInfo.
 * Follows Builder pattern for construction.
 */
public final class SimdInfoImpl implements SimdInfo {
    
    private final String operatingSystem;
    private final String architecture;
    private final String javaVersion;
    private final boolean avx512Supported;
    private final boolean avx2Supported;
    private final boolean avxSupported;
    private final boolean sse4Supported;
    private final boolean sse2Supported;
    private final boolean neonSupported;
    private final String bestSimdInstructionSet;

    private SimdInfoImpl(Builder builder) {
        this.operatingSystem = builder.operatingSystem;
        this.architecture = builder.architecture;
        this.javaVersion = builder.javaVersion;
        this.avx512Supported = builder.avx512Supported;
        this.avx2Supported = builder.avx2Supported;
        this.avxSupported = builder.avxSupported;
        this.sse4Supported = builder.sse4Supported;
        this.sse2Supported = builder.sse2Supported;
        this.neonSupported = builder.neonSupported;
        this.bestSimdInstructionSet = builder.bestSimdInstructionSet;
    }

    @Override
    public String getOperatingSystem() { 
        return operatingSystem; 
    }
    
    @Override
    public String getArchitecture() { 
        return architecture; 
    }
    
    @Override
    public String getJavaVersion() { 
        return javaVersion; 
    }
    
    @Override
    public boolean isAvx512Supported() { 
        return avx512Supported; 
    }
    
    @Override
    public boolean isAvx2Supported() { 
        return avx2Supported; 
    }
    
    @Override
    public boolean isAvxSupported() { 
        return avxSupported; 
    }
    
    @Override
    public boolean isSse4Supported() { 
        return sse4Supported; 
    }
    
    @Override
    public boolean isSse2Supported() { 
        return sse2Supported; 
    }
    
    @Override
    public boolean isNeonSupported() { 
        return neonSupported; 
    }
    
    @Override
    public String getBestSimdInstructionSet() { 
        return bestSimdInstructionSet; 
    }

    @Override
    public boolean hasSimdSupport() {
        return !"NONE".equals(bestSimdInstructionSet);
    }

    @Override
    public String toString() {
        return String.format("SimdInfo{os='%s', arch='%s', bestSIMD='%s', AVX512=%s, AVX2=%s, AVX=%s, SSE4=%s, SSE2=%s, NEON=%s}",
            operatingSystem, architecture, bestSimdInstructionSet, 
            avx512Supported, avx2Supported, avxSupported, sse4Supported, sse2Supported, neonSupported);
    }

    /**
     * Builder for SimdInfoImpl.
     */
    public static final class Builder {
        private String operatingSystem;
        private String architecture;
        private String javaVersion;
        private boolean avx512Supported;
        private boolean avx2Supported;
        private boolean avxSupported;
        private boolean sse4Supported;
        private boolean sse2Supported;
        private boolean neonSupported;
        private String bestSimdInstructionSet = "NONE";

        public Builder setOperatingSystem(String operatingSystem) {
            this.operatingSystem = operatingSystem;
            return this;
        }

        public Builder setArchitecture(String architecture) {
            this.architecture = architecture;
            return this;
        }

        public Builder setJavaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }

        public Builder setAvx512Supported(boolean avx512Supported) {
            this.avx512Supported = avx512Supported;
            return this;
        }

        public Builder setAvx2Supported(boolean avx2Supported) {
            this.avx2Supported = avx2Supported;
            return this;
        }

        public Builder setAvxSupported(boolean avxSupported) {
            this.avxSupported = avxSupported;
            return this;
        }

        public Builder setSse4Supported(boolean sse4Supported) {
            this.sse4Supported = sse4Supported;
            return this;
        }

        public Builder setSse2Supported(boolean sse2Supported) {
            this.sse2Supported = sse2Supported;
            return this;
        }

        public Builder setNeonSupported(boolean neonSupported) {
            this.neonSupported = neonSupported;
            return this;
        }

        public Builder setBestSimdInstructionSet(String bestSimdInstructionSet) {
            this.bestSimdInstructionSet = bestSimdInstructionSet;
            return this;
        }

        public SimdInfoImpl build() {
            return new SimdInfoImpl(this);
        }
    }
}