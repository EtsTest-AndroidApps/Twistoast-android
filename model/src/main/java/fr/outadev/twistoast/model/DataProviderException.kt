/*
 * Twistoast - DataProviderException.kt
 * Copyright (C) 2013-2018 Baptiste Candellier
 *
 * Twistoast is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Twistoast is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.outadev.twistoast.model

/**
 * Thrown when an error was encountered while fetching data from the API.
 *
 * @author outadoc
 */
open class DataProviderException constructor(s: String = "") : Exception(s) {

    var errorCode: String = ""

    constructor(errorCode: String, message: String) : this(message) {
        this.errorCode = errorCode
    }

    override fun toString(): String {
        return "NavitiaException: [$errorCode] $message"
    }
}
