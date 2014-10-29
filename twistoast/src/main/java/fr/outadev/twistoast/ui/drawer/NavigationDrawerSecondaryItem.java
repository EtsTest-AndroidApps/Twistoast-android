/*
 * Twistoast - NavigationDrawerSecondaryItem
 * Copyright (C) 2013-2014  Baptiste Candellier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.outadev.twistoast.ui.drawer;

import android.support.annotation.StringRes;

/**
 * A secondary navigation drawer item.
 * Useful for pages like preferences or help.
 *
 * @author outadoc
 */
public class NavigationDrawerSecondaryItem extends NavigationDrawerFragmentItem {

	/**
	 * Creates a new NavigationDrawerSecondaryItem.
	 *
	 * @param titleResId         the id of the string resource for the title
	 * @param classToInstantiate the Class object of the Fragment to return with getFragment
	 */
	public NavigationDrawerSecondaryItem(@StringRes int titleResId, Class classToInstantiate) {
		super(-1, titleResId, classToInstantiate);
	}

}
