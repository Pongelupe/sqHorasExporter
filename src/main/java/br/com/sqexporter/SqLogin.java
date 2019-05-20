package br.com.sqexporter;

import br.com.sqexporter.services.Auth;
import lombok.Data;

@Data
public class SqLogin implements Auth {

	private String tokenType;

	private String tokenAccess;

}
