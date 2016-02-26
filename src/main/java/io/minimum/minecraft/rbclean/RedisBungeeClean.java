// Licensed under the Unlicense; see LICENSE in the main directory.
package io.minimum.minecraft.rbclean;

import com.google.gson.Gson;
import org.apache.commons.cli.*;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

public class RedisBungeeClean {
    private static final Gson gson = new Gson();

    public static void main(String... args) {
        Options options = new Options();

        Option hostOption = new Option("h", "host", true, "Sets the Redis host to use.");
        hostOption.setRequired(true);
        options.addOption(hostOption);

        Option portOption = new Option("p", "port", true, "Sets the Redis port to use.");
        options.addOption(portOption);

        Option passwordOption = new Option("w", "password", true, "Sets the Redis password to use.");
        options.addOption(passwordOption);

        Option dryRunOption = new Option("d", "dry-run", false, "Performs a dry run (no data is modified).");
        options.addOption(dryRunOption);

        CommandLine commandLine;

        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("RedisBungeeClean", options);
            return;
        }

        ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        int port = commandLine.hasOption('p') ? Integer.parseInt(commandLine.getOptionValue('p')) : 6379;

        try (Jedis jedis = new Jedis(commandLine.getOptionValue('h'), port, 0)) {
            if (commandLine.hasOption('w')) {
                jedis.auth(commandLine.getOptionValue('w'));
            }

            System.out.println("Fetching UUID cache...");
            Map<String, String> uuidCache = jedis.hgetAll("uuid-cache");

            // Just in case we need it, compress everything in JSON format.
            if (!commandLine.hasOption('d')) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
                File file = new File("uuid-cache-previous-" + dateFormat.format(new Date()) + ".json.gz");
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    System.out.println("Can't write backup of the UUID cache, will NOT proceed.");
                    e.printStackTrace();
                    return;
                }

                System.out.println("Creating backup (as " + file.getName() + ")...");

                try (OutputStreamWriter bw = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file)))) {
                    gson.toJson(uuidCache, bw);
                } catch (IOException e) {
                    System.out.println("Can't write backup of the UUID cache, will NOT proceed.");
                    e.printStackTrace();
                    return;
                }
            }

            System.out.println("Cleaning out the bird cage (this may take a while...)");
            int originalSize = uuidCache.size();

            Map<String, Future<Boolean>> results = new HashMap<>();

            for (final Map.Entry<String, String> e : uuidCache.entrySet()) {
                FutureTask<Boolean> task = new FutureTask<>(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return gson.fromJson(e.getValue(), CachedUUIDEntry.class).expired();
                    }
                });
                service.execute(task);
                results.put(e.getKey(), task);
            }

            for (Map.Entry<String, Future<Boolean>> entry : results.entrySet()) {
                boolean res;
                try {
                    res = entry.getValue().get();
                } catch (InterruptedException | ExecutionException e) {
                    // ignore it
                    System.out.println("Error found:");
                    e.printStackTrace();
                    res = true;
                }

                if (res) {
                    uuidCache.remove(entry.getKey());
                }
            }

            int newSize = uuidCache.size();

            if (commandLine.hasOption('d')) {
                System.out.println((originalSize - newSize) + " records would be expunged if a dry run was not conducted.");
            } else {
                System.out.println("Expunging " + (originalSize - newSize) + " records...");
                jedis.del("uuid-cache");
                jedis.hmset("uuid-cache", uuidCache);
                System.out.println("Expunging complete.");
            }
        } finally {
            service.shutdownNow();
        }
    }

}
