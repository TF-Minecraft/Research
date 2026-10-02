package net.tfminecraft.research;

import net.tfminecraft.research.loader.*;
import net.tfminecraft.research.registry.AspectItemRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Restores process-wide configuration and registries after an isolated test. */
public final class ResearchTestState implements AutoCloseable {
    private final Map<Field, Object> values = new LinkedHashMap<>();

    public ResearchTestState() throws IllegalAccessException {
        for (Class<?> type : new Class<?>[]{Research.class, Cache.class, GuiCache.class, Messages.class,
                AspectLoader.class, InputLoader.class, OutputLoader.class, TemplateLoader.class, AspectItemRegistry.class}) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                Object value = field.get(null);
                if (Modifier.isFinal(field.getModifiers())) {
                    if (value instanceof Map<?, ?> map) values.put(field, new LinkedHashMap<>(map));
                } else {
                    values.put(field, value);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void close() throws IllegalAccessException {
        for (var saved : values.entrySet()) {
            if (Modifier.isFinal(saved.getKey().getModifiers())) {
                Map<Object, Object> map = (Map<Object, Object>) saved.getKey().get(null);
                map.clear();
                map.putAll((Map<Object, Object>) saved.getValue());
            } else {
                saved.getKey().set(null, saved.getValue());
            }
        }
    }
}
