### VelocityVoteListener
This [Velocity](https://github.com/PaperMC/velocity) plugin was designed in order to supply all of the necessary components for a Votifier/[NuVotifier](https://github.com/nuvotifier/nuvotifier) server without the need for a "vote listener" plugin *on the backend*, thus cutting out the Votifier dependency and reducing the total amount of compute usage for each of Aedificium's backend servers.

#### How it works
It requires a [modified version of NuVotifier](https://github.com/aokod/NuVotifier) that is able to listen on **multiple inbound ports** which may now be *labeled* in the TOML configuration.  Since individual votes are tied to whichever listener sent them, `forwarding.proxy` targets may now populate `listen` with the appropriate `listenerLabel` in order to direct votes to their respective backend servers.

#### Practical limitations
Because this is a Velocity plugin, we are limited by the API as to exactly what can be done to the players upon casting a vote.  So, we can't execute commands without using a backend vote listener (or something like RCON), however, it's still possible to send private messages (to the player that casted the vote) and public messages (to every player in the backend server).

This configuration is aimed to be as flexible as possible whilst remaining within the scope of said limitations.

#### List of commands
There is only one command: `/votereload`, which may be executed from console to reload the plugin configuration.

Before using this plugin, one should keep in mind that it was designed for a specific use case and most likely will not serve any practical benefit to your server setup, unless you know what you're doing.  It will **not** work with BungeeCord.
