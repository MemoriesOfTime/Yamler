package net.cubespace.Yamler.Config.Converter;

import net.cubespace.Yamler.Config.ConfigSection;
import net.cubespace.Yamler.Config.InternalConverter;
import net.cubespace.Yamler.Config.YamlConfig;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Map;

/**
 * @author geNAZt (fabian.fassbender42@googlemail.com)
 */
public class Config implements Converter {
    private InternalConverter internalConverter;

    public Config(InternalConverter internalConverter) {
        this.internalConverter = internalConverter;
    }

    @Override
    public Object toConfig(Class<?> type, Object obj, ParameterizedType parameterizedType) throws Exception {
        return (obj instanceof Map) ? obj : ((YamlConfig) obj).saveToMap( obj.getClass() );
    }

    @Override
    public Object fromConfig(Class<?> type, Object section, ParameterizedType genericType) throws Exception {
        YamlConfig obj = (YamlConfig) newInstance(type);

        // Inject Converter stack into subconfig
        for (Class<? extends Converter> aClass : internalConverter.getCustomConverters()) {
            obj.addConverter(aClass);
        }

        obj.loadFromMap((section instanceof Map) ? (Map<?, ?>) section : ((ConfigSection) section).getRawMap(), type);
        return obj;
    }

    // recursively handles enclosed classes
    public Object newInstance(Class<?> type) throws Exception {
        Class<?> enclosingClass = type.getEnclosingClass();
        // Only non-static inner (member) classes need an enclosing instance passed
        // to their constructor. Static nested classes share the same
        // getEnclosingClass() but take a no-arg constructor, so the previous
        // unguarded check incorrectly tried to resolve a synthetic
        // (EnclosingClass) constructor for them and threw NoSuchMethodException.
        if (enclosingClass != null && !Modifier.isStatic(type.getModifiers())) {
            Object instanceOfEnclosingClass = newInstance(enclosingClass);
            return type.getDeclaredConstructor(enclosingClass).newInstance(instanceOfEnclosingClass);
        } else {
            return type.getDeclaredConstructor().newInstance();
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return YamlConfig.class.isAssignableFrom(type);
    }
}
