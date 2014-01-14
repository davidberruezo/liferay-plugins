/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.repository.external.api;

/**
 * @author Iván Zaera
 * @author Sergio González
 */
public final class ExtRepositoryModelType<T extends ExtRepositoryModel> {

	public static final ExtRepositoryModelType<ExtRepositoryEntry> ENTRY =
		new ExtRepositoryModelType<ExtRepositoryEntry>("ENTRY");

	public static final ExtRepositoryModelType<ExtRepositoryFileEntry> FILE =
		new ExtRepositoryModelType<ExtRepositoryFileEntry>("FILE");

	public static final ExtRepositoryModelType<ExtRepositoryFolder> FOLDER =
		new ExtRepositoryModelType<ExtRepositoryFolder>("FOLDER");

	@Override
	public String toString() {
		return _name;
	}

	private ExtRepositoryModelType(String name) {
		_name = name;
	}

	private String _name;

}