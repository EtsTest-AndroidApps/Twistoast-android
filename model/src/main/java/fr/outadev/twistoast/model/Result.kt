/*
 * Twistoast - Result.kt
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

sealed class Result<T> {
    data class Success<T>(val data: T): Result<T>()
    data class Failure<T>(val e: Throwable): Result<T>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> failure(e: Throwable): Result<T> = Failure(e)
    }
}