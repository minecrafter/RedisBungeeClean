# RedisBungeeClean

This tool cleans old RedisBungee data. Currently, only the UUID cache is cleaned (old data is expired).

## Usage

    usage: RedisBungeeClean
     -d,--dry-run          Performs a dry run (no data is modified).
     -h,--host <arg>       Sets the Redis host to use.
     -p,--port <arg>       Sets the Redis port to use.
     -w,--password <arg>   Sets the Redis password to use.

Only a host is required. Port and password options can be specified if your Redis server is running on another port
or has a password. You can also perform a dry run.