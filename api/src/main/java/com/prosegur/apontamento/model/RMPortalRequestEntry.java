package com.prosegur.apontamento.model;

import java.util.List;

import org.apache.http.NameValuePair;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class RMPortalRequestEntry {

	private String day;
	private List<NameValuePair> params;
}
