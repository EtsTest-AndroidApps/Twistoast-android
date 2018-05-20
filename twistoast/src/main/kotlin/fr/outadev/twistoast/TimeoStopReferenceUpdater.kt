/*
 * Twistoast - TimeoStopReferenceUpdater.kt
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

package fr.outadev.twistoast

import android.content.Context
import android.util.Log
import fr.outadev.android.transport.ITimeoRequestHandler
import fr.outadev.android.transport.TimeoRequestHandler
import fr.outadev.twistoast.model.DataProviderException
import fr.outadev.twistoast.model.Stop
import fr.outadev.twistoast.persistence.IStopRepository
import fr.outadev.twistoast.persistence.StopRepository
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Fetches and updates all the references of the stops saved in our database.
 * Useful since they periodically change, and this class should allow the user
 * to update his list of stops without having to delete/re-add them.
 */
class TimeoStopReferenceUpdater(context: Context = ApplicationTwistoast.instance) {

    private val database: IStopRepository = StopRepository()
    private val requestHandler: ITimeoRequestHandler = TimeoRequestHandler()

    /**
     * Updates all the references of the bus stops in the database.
     * Read the classe's Javadoc for some more context.
     *
     * @throws XmlPullParserException
     * @throws IOException
     */
    @Throws(XmlPullParserException::class, IOException::class, DataProviderException::class)
    fun updateAllStopReferences(stops: List<Stop>): Int {
        //update the progress
        Log.i(TAG, "updating stop references for ${stops.size} stops")

        //get the stops for the current line
        var nbUpdated = 0
        val updatedStops = requestHandler.getStopsByCode(codes = stops.map(Stop::id))

        //update all the stops we received.
        //obviously, only the ones existing in the database will be updated.
        updatedStops.forEach {
            stop -> nbUpdated += database.updateStopReference(stop)
        }

        Log.i(TAG, "$nbUpdated stop references updated out of ${stops.size}")

        return nbUpdated
    }

    companion object {
        private val TAG = TimeoStopReferenceUpdater::class.java.simpleName
    }

}
