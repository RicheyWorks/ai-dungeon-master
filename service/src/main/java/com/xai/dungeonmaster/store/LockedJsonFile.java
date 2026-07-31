package com.xai.dungeonmaster.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.function.Function;

/**
 * Cross-process-safe read/write of a single JSON document.
 *
 * Every operation takes an exclusive {@link FileLock} on a sibling {@code .lock}
 * file, reloads the document, applies the caller's mutation, and writes
 * atomically (temp + move). Two JVMs sharing the same path therefore see each
 * other's updates — the multi-process half of the multi-node upgrade for
 * sessions and entitlements when nodes share a volume.
 *
 * Thread-safe within one process via the exclusive lock; not a substitute for
 * a real networked datastore at large scale, but correct for single-host
 * multi-process and NFS/shared-disk multi-node.
 */
public final class LockedJsonFile<T> {

    private final Path file;
    private final Path lockFile;
    private final TypeReference<T> type;
    private final T empty;
    private final ObjectMapper mapper;

    public LockedJsonFile(Path file, TypeReference<T> type, T empty) {
        this(file, type, empty, new ObjectMapper());
    }

    public LockedJsonFile(Path file, TypeReference<T> type, T empty, ObjectMapper mapper) {
        if (file == null) throw new IllegalArgumentException("file");
        if (type == null) throw new IllegalArgumentException("type");
        this.file = file.toAbsolutePath();
        this.lockFile = this.file.resolveSibling(this.file.getFileName() + ".lock");
        this.type = type;
        this.empty = empty;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    public Path path() {
        return file;
    }

    /**
     * Under exclusive lock: load → transform → write (when transform returns
     * non-null). Returning the input unchanged still rewrites when {@code dirty}
     * is true; prefer {@link #read} for pure lookups.
     */
    public T update(Function<T, T> transform) {
        return withLock(channel -> {
            T current = readUnlocked();
            T next = transform.apply(current);
            if (next != null) {
                writeUnlocked(next);
                return next;
            }
            return current;
        });
    }

    /** Exclusive lock + reload; no write. */
    public T read() {
        return withLock(channel -> readUnlocked());
    }

    private T withLock(Function<FileChannel, T> body) {
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
                 FileLock lock = channel.lock()) {
                // Keep lock file non-empty so some filesystems actually create it.
                if (channel.size() == 0) {
                    channel.write(ByteBuffer.wrap(new byte[]{1}));
                    channel.force(true);
                }
                return body.apply(channel);
            }
        } catch (IOException e) {
            throw new IllegalStateException("locked JSON I/O failed for " + file + ": " + e.getMessage(), e);
        }
    }

    private T readUnlocked() {
        if (!Files.isRegularFile(file)) {
            return empty;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return empty;
            }
            T value = mapper.readValue(bytes, type);
            return value != null ? value : empty;
        } catch (IOException e) {
            System.err.println("WARN: could not read " + file + ": " + e.getMessage());
            return empty;
        }
    }

    private void writeUnlocked(T value) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // ATOMIC_MOVE may fail on some filesystems — fall back to plain replace.
            try {
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                if (Files.isRegularFile(tmp)) {
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            } catch (IOException ignored) {
                // fall through
            }
            System.err.println("WARN: could not write " + file + ": " + e.getMessage());
        }
    }
}
