package com.oceanli.springframework.service.impl;

import com.oceanli.springframework.annotation.GPService;
import com.oceanli.springframework.service.IDemoService;

/**
 * 核心业务逻辑
 */
@GPService
public class DemoService implements IDemoService {

	public String get(String name) {
		return "My name is " + name;
	}

}
