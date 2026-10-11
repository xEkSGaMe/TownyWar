package ru.example.townywar.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MessagesManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;

    /** Кэш сырых шаблонов из конфига: ключ → строка. Инвалидируется в reload(). */
    private final Map<String, String> rawCache = new ConcurrentHashMap<>();

    /** Кэшированный prefix-компонент (вычисляется один раз при reload). */
    private volatile Component prefixComponent = Component.empty();

    public MessagesManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Пересобирает prefix и очищает кэш шаблонов.
     * Вызывается при создании и из /twar reload.
     */
    public void reload() {
        rawCache.clear();
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        prefixComponent = (prefix == null || prefix.isBlank())
                ? Component.empty()
                : MM.deserialize(prefix);
    }

    private String getRaw(String key) {
        String cached = rawCache.get(key);
        if (cached != null) return cached;
        String raw = plugin.getConfig().getString("messages." + key, "<red>[missing:" + key + "]</red>");
        if (raw == null) raw = "";
        rawCache.put(key, raw);
        return raw;
    }

    public Component format(String key, String... pairs) {
        if ((pairs.length & 1) != 0) throw new IllegalArgumentException("pairs must be even");

        String raw = getRaw(key);

        List<TagResolver> resolvers = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolvers.add(Placeholder.parsed(pairs[i],
                    MiniMessage.miniMessage().escapeTags(pairs[i + 1] == null ? "" : pairs[i + 1])));
        }

        Component body = MM.deserialize(raw, TagResolver.resolver(resolvers));
        return prefixComponent.append(body);
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(format(key, pairs));
    }
}