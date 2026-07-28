package net.cubespace.Yamler.Config.Converter;

import net.cubespace.Yamler.Config.InternalConverter;

import java.lang.reflect.ParameterizedType;
import java.util.HashSet;

/**
 * @author geNAZt (fabian.fassbender42@googlemail.com)
 */
public class Primitive implements Converter {
    private HashSet<String> types = new HashSet<>() {{
        add("boolean");
        add("char");
        add("byte");
        add("short");
        add("int");
        add("long");
        add("float");
        add("double");
    }};

    private InternalConverter internalConverter;

    public Primitive(InternalConverter internalConverter) {
        this.internalConverter = internalConverter;
    }

    @Override
    public Object toConfig(Class<?> type, Object obj, ParameterizedType parameterizedType) throws Exception {
        return obj;
    }

    @Override
    public Object fromConfig(Class<?> type, Object section, ParameterizedType genericType) throws Exception {
        switch(type.getSimpleName()) {
            case "short":
                return (section instanceof Short) ? section : Integer.valueOf((int) section).shortValue();
            case "byte":
                return (section instanceof Byte) ? section : Integer.valueOf((int) section).byteValue();
            case "float":
                if ( section instanceof Integer ) {
                    return Double.valueOf( (int) section ).floatValue();
                }

                return (section instanceof Float) ? section : Double.valueOf((double) section).floatValue();
            case "char":
                return (section instanceof Character) ? section : ((String) section).charAt(0);
            default:
                return section;
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return types.contains(type.getName());
    }
}
