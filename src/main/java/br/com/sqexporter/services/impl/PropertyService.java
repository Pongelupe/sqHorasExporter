package br.com.sqexporter.services.impl;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import br.com.sqexporter.services.BaseService;
import br.com.sqexporter.services.IPropertyService;

public class PropertyService implements IPropertyService {

	private static final String SQ_EXPORTER_PROPERTIES = "/sq.properties";

	private Properties prop;

	public PropertyService() {
		try {
			prop = new Properties();
			FileInputStream fileInputStream = new FileInputStream(
					PropertyService.class.getResource(SQ_EXPORTER_PROPERTIES)
							.getFile());
			prop.load(fileInputStream);
			fileInputStream.close();
		} catch (IOException e) {
			throw new IllegalArgumentException(
					"Error loading " + SQ_EXPORTER_PROPERTIES + " file");
		}
	}

	public Properties getProp() {
		return prop;
	}

	@Override
	public Optional<String> getProp(String propKey) {
		return Optional.ofNullable(this.prop.get(propKey))
				.map(Object::toString);
	}

	@Override
	public void setProp(String propKey, String value) {
		this.prop.put(propKey, value);
	}

	@Override
	public void setPropEager(String propKey, String value) {
		try {
			this.setProp(propKey, value);
			commitChanges();
		} catch (IOException e) {
			this.setProp(propKey, value);
		}
	}

	@Override
	public void commitChanges() throws IOException {
		this.prop.store(new FileOutputStream(
				BaseService.class.getResource(SQ_EXPORTER_PROPERTIES).getFile()),
				null);
	}

}
