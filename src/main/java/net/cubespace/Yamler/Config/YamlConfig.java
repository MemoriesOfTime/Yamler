package net.cubespace.Yamler.Config;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;

public class YamlConfig extends ConfigMapper implements IConfig {
	public YamlConfig() {

	}

	public YamlConfig(String filename) {
		CONFIG_FILE = new File(filename + (filename.endsWith(".yml") ? "" : ".yml"));
	}


	/**
	 * Resolve the config path for a field based on {@link #CONFIG_MODE} and an optional {@link Path} annotation.
	 * <p>
	 * Centralizing this avoids the three call sites (collectComments, internalSave, internalLoad) drifting
	 * apart, which previously caused comments to be registered under a different key than the data when
	 * CONFIG_MODE is DEFAULT and the field name contains underscores.
	 */
	private String resolvePath(Field field) {
		String path;

		switch (CONFIG_MODE) {
			case PATH_BY_UNDERSCORE:
				path = field.getName().replace("_", ".");
				break;
			case FIELD_IS_KEY:
				path = field.getName();
				break;
			case DEFAULT:
			default:
				String fieldName = field.getName();
				if (fieldName.contains("_")) {
					path = field.getName().replace("_", ".");
				} else {
					path = field.getName();
				}
				break;
		}

		if (field.isAnnotationPresent(Path.class)) {
			path = field.getAnnotation(Path.class).value();
		}

		return path;
	}

	public void save(boolean withComments) throws InvalidConfigurationException {
		if (CONFIG_FILE == null) {
			throw new IllegalArgumentException("Saving a config without given File");
		}

		if (root == null) {
			root = new ConfigSection();
		}

		clearComments();
		if (withComments) collectComments(getClass());

		internalSave(getClass());
		saveToYaml();
	}

	@Override
	public void save() throws InvalidConfigurationException {
		this.save(true);
	}

	private void collectComments(Class<?> clazz) {
		if (!clazz.getSuperclass().equals(YamlConfig.class)) {
			collectComments(clazz.getSuperclass());
		}

		for (Field field : clazz.getDeclaredFields()) {
			if (doSkip(field)) {
				continue;
			}

			String path = resolvePath(field);

			ArrayList<String> comments = new ArrayList<>();
			for (Annotation annotation : field.getAnnotations()) {
				if (annotation instanceof Comment) {
					comments.add(((Comment) annotation).value());
				}

				if (annotation instanceof Comments) {
					comments.addAll(Arrays.asList(((Comments) annotation).value()));
				}
			}

			if (!comments.isEmpty()) {
				for (String comment : comments) {
					addComment(path, comment);
				}
			}
		}
	}

	private void internalSave(Class<?> clazz) throws InvalidConfigurationException {
		if (!clazz.getSuperclass().equals(YamlConfig.class)) {
			internalSave(clazz.getSuperclass());
		}

		for (Field field : clazz.getDeclaredFields()) {
			if (doSkip(field)) {
				continue;
			}

			String path = resolvePath(field);

			if (Modifier.isPrivate(field.getModifiers())) {
				field.setAccessible(true);
			}

			try {
				converter.toConfig(this, field, root, path);
				converter.fromConfig(this, field, root, path);
			} catch (Exception e) {
				if (!skipFailedObjects) {
					throw new InvalidConfigurationException("Could not save the Field", e);
				}
			}
		}
	}

	@Override
	public void save(File file) throws InvalidConfigurationException {
		if (file == null) {
			throw new IllegalArgumentException("File argument can not be null");
		}

		CONFIG_FILE = file;
		save();
	}

	@Override
	public void init() throws InvalidConfigurationException {
		if (!CONFIG_FILE.exists()) {
			if (CONFIG_FILE.getParentFile() != null) {
				CONFIG_FILE.getParentFile().mkdirs();
			}

			try {
				CONFIG_FILE.createNewFile();
				save();
			} catch (IOException e) {
				throw new InvalidConfigurationException("Could not create new empty Config", e);
			}
		} else {
			load();
		}
	}

	@Override
	public void init(File file) throws InvalidConfigurationException {
		if (file == null) {
			throw new IllegalArgumentException("File argument can not be null");
		}

		CONFIG_FILE = file;
		init();
	}

	@Override
	public void reload() throws InvalidConfigurationException {
		loadFromYaml();
		internalLoad(getClass());
	}

	@Override
	public void load() throws InvalidConfigurationException {
		if (CONFIG_FILE == null) {
			throw new IllegalArgumentException("Loading a config without given File");
		}

		loadFromYaml();
		update(root);
		internalLoad(getClass());
	}

	private void internalLoad(Class<?> clazz) throws InvalidConfigurationException {
		if (!clazz.getSuperclass().equals(YamlConfig.class)) {
			internalLoad(clazz.getSuperclass());
		}

		boolean save = false;
		for (Field field : clazz.getDeclaredFields()) {
			if (doSkip(field)) {
				continue;
			}

			String path = resolvePath(field);

			if (Modifier.isPrivate(field.getModifiers())) {
				field.setAccessible(true);
			}

			if (root.has(path)) {
				try {
					converter.fromConfig(this, field, root, path);
				} catch (Exception e) {
					throw new InvalidConfigurationException("Could not set field " + field.getName(), e);
				}
			} else {
				try {
					converter.toConfig(this, field, root, path);
					converter.fromConfig(this, field, root, path);

					save = true;
				} catch (Exception e) {
					if (!skipFailedObjects) {
						throw new InvalidConfigurationException("Could not get field", e);
					}
				}
			}
		}

		if (save) {
			clearComments();
			collectComments(getClass());
			saveToYaml();
		}
	}

	@Override
	public void load(File file) throws InvalidConfigurationException {
		if (file == null) {
			throw new IllegalArgumentException("File argument can not be null");
		}

		CONFIG_FILE = file;
		load();
	}
}
