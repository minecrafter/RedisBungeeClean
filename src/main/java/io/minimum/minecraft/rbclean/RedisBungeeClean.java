// Licensed under the Unlicense; see LICENSE in the main directory.
package io.minimum.minecraft.rbclean;

import com.google.gson.Gson;
import org.apache.commons.cli.*;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class RedisBungeeClean {
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

        int port = commandLine.hasOption('p') ? Integer.parseInt(commandLine.getOptionValue('p')) : 6379;

        try (Jedis jedis = new Jedis(commandLine.getOptionValue('h'), port, 0)) {
            if (commandLine.hasOption('w')) {
                jedis.auth(commandLine.getOptionValue('w'));
            }

            System.out.println("Fetching UUID cache...");
            Map<String, String> uuidCache = jedis.hgetAll("uuid-cache");
            Gson gson = new Gson();

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
                    bw.write(gson.toJson(uuidCache));
                } catch (IOException e) {
                    System.out.println("Can't write backup of the UUID cache, will NOT proceed.");
                    e.printStackTrace();
                    return;
                }
            }

            System.out.println("Cleaning out the bird cage (this may take a while...)");
            int originalSize = uuidCache.size();
            for (Iterator<Map.Entry<String, String>> it = uuidCache.entrySet().iterator(); it.hasNext(); ) {
                CachedUUIDEntry entry = gson.fromJson(it.next().getValue(), CachedUUIDEntry.class);

                if (entry.expired()) {
                    it.remove();
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
        }
    }

    private class CachedUUIDEntry {
        private final String name;
        private final UUID uuid;
        private final Calendar expiry;

        private CachedUUIDEntry(String name, UUID uuid, Calendar expiry) {
            this.name = name;
            this.uuid = uuid;
            this.expiry = expiry;
        }

        public boolean expired() {
            return Calendar.getInstance().after(expiry);
        }
    }
}
