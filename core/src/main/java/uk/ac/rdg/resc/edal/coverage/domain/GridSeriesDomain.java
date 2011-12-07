/*******************************************************************************
 * Copyright (c) 2011 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/

package uk.ac.rdg.resc.edal.coverage.domain;

import java.util.List;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import uk.ac.rdg.resc.edal.coverage.GridSeriesCoverage;
import uk.ac.rdg.resc.edal.coverage.grid.GridCell4D;
import uk.ac.rdg.resc.edal.coverage.grid.GridCoordinates4D;
import uk.ac.rdg.resc.edal.coverage.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.coverage.grid.TimeAxis;
import uk.ac.rdg.resc.edal.coverage.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.position.GeoPosition;
import uk.ac.rdg.resc.edal.position.CalendarSystem;
import uk.ac.rdg.resc.edal.position.VerticalCrs;

/**
 * The domain of a {@link GridSeriesCoverage}, modelled as a composition of a
 * horizontal grid, plus t and z axes.
 * 
 * @todo Explain that GridCoordinates are 4D and explain ordering.
 * @author Jon Blower
 */
public interface GridSeriesDomain extends DiscreteDomain<GeoPosition, GridCell4D> {

    /**
     * @return the horizontal component of this domain
     */
    public HorizontalGrid getHorizontalGrid();

    /**
     * @return the vertical component of this domain
     */
    public VerticalAxis getVerticalAxis();

    /**
     * @return the time component of this domain
     */
    public TimeAxis getTimeAxis();

    /**
     * @return the {@link CoordinateReferenceSystem} of the horizontal component
     *         of this domain
     */
    public CoordinateReferenceSystem getHorizontalCrs();

    /**
     * @return the {@link VerticalCrs} of the vertical component of this domain
     */
    public VerticalCrs getVerticalCrs();

    /**
     * @return the {@link CalendarSystem} of the time component of this domain
     */
    public CalendarSystem getCalendarSystem();

    /**
     * Find the {@link GridCell4D} which contains the desired position
     * 
     * @param pos
     *            the position whose grid cell is wanted
     * @return the resultant {@link GridCell4D}
     */
    public GridCell4D findContainingCell(GeoPosition pos);

    /**
     * {@inheritDoc}
     * <p>
     * Use this method with caution, as it is possible that the number of domain
     * objects (grid cells) will exceed {@link Integer#MAX_VALUE}, meaning that
     * the value of {@code size()} in the returned List may be inaccurate.
     * Prefer the use of {@link #size()} instead, which returns a long integer.
     * </p>
     * 
     * @return a list of the {@link GridCell4D} objects which comprise this
     *         domain
     */
    @Override
    public List<GridCell4D> getDomainObjects();

    /**
     * This method returns the overall index of a point with the specified axis indices
     * @param hIndex the index in the horizontal grid
     * @param vIndex the index in the vertical axis
     * @param tIndex the index in the time axis
     * @return the overall index, which will correspond to the position in findIndexOf()
     */
    public long getIndex(long hIndex, int vIndex, int tIndex);
    
    public GridCoordinates4D getComponentsOf(long index);
}
