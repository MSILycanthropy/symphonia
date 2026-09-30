package io.github.msilycanthropy.symphonia;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import io.github.msilycanthropy.symphonia.commands.CounterCommand;
import io.github.msilycanthropy.symphonia.commands.ExampleCommand;
import io.github.msilycanthropy.symphonia.config.ExampleConfig;
import io.github.msilycanthropy.symphonia.events.ExampleEvent;
import javax.annotation.Nonnull;

public class Symphonia extends JavaPlugin {

  private static Config<ExampleConfig> config = null;

  public Symphonia(@Nonnull JavaPluginInit init) {
    super(init);
    config = this.withConfig("example_config", ExampleConfig.CODEC);
  }

  @Override
  protected void setup() {
    config.save();

    getLogger().at(java.util.logging.Level.INFO).log(Hello.greet(getName()));

    this.getCommandRegistry().registerCommand(
      new ExampleCommand("example", "An example command")
    );
    this.getCommandRegistry().registerCommand(new CounterCommand());

    if (getConfig().get().isEnabledWelcomeMessage()) {
      this.getEventRegistry().registerGlobal(
        PlayerReadyEvent.class,
        ExampleEvent::onPlayerReady
      );
    }
  }

  public static Config<ExampleConfig> getConfig() {
    return config;
  }
}
