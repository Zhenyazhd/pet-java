package com.jobsearch.latexworker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A compile that is cancelled (the worker is stopping) must not leave tectonic running on the host. */
class CompilerInterruptTest {

	@TempDir
	Path dir;

	@Test
	void interruptingACompileKillsTheProcessAndItsChildren() throws Exception {
		Path pidFile = dir.resolve("pids");
		// Stands in for tectonic: records its own pid and its child's, then waits far longer than the test.
		Path fake = dir.resolve("fake-tectonic");
		Files.writeString(fake, "#!/bin/sh\nsleep 60 &\necho \"$$ $!\" > '" + pidFile + "'\nwait\n");
		Files.setPosixFilePermissions(fake, PosixFilePermissions.fromString("rwxr-xr-x"));
		Compiler compiler = new Compiler(fake.toString(), Duration.ofSeconds(60), 1024);
		AtomicReference<Throwable> thrown = new AtomicReference<>();
		Thread compile = new Thread(() -> {
			try {
				compiler.compile("\\documentclass{article}\\begin{document}x\\end{document}");
			}
			catch (Throwable t) {
				thrown.set(t);
			}
		});

		compile.start();
		long[] pids = waitForPids(pidFile);
		compile.interrupt();
		compile.join(5_000);

		assertFalse(compile.isAlive());
		assertTrue(thrown.get() instanceof InterruptedException, String.valueOf(thrown.get()));
		for (long pid : pids) {
			assertTrue(waitUntilGone(pid), "process " + pid + " is still running after the compile was interrupted");
		}
	}

	private static long[] waitForPids(Path pidFile) throws Exception {
		for (int i = 0; i < 100; i++) {
			if (Files.exists(pidFile) && !Files.readString(pidFile).isBlank()) {
				String[] parts = Files.readString(pidFile).trim().split("\\s+");
				return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1])};
			}
			Thread.sleep(50);
		}
		throw new AssertionError("the fake compiler never started");
	}

	private static boolean waitUntilGone(long pid) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			if (ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isEmpty()) {
				return true;
			}
			Thread.sleep(50);
		}
		return false;
	}
}
