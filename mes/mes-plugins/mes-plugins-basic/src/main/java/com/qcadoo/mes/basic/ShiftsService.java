/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.basic;

import com.qcadoo.mes.basic.ShiftsServiceImpl.ShiftHour;
import com.qcadoo.mes.basic.shift.Shift;
import com.qcadoo.model.api.Entity;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;

//FIXME maku: replace bounded time/date ranges with JodaTime's intervals.
public interface ShiftsService {

    List<Shift> findAll();

    List<Shift> findAll(final Entity productionLine);

    List<Entity> getShifts();

    List<Entity> getShiftsWorkingAtDate(final Date date);

    LocalTime[][] convertDayHoursToInt(final String string);

    Entity getShiftFromDateWithTime(final Date date);

    // FIXME maku: ugly coupling - interface uses type defined within one of concrete implementations
    List<ShiftHour> getHoursForAllShifts(final Date dateFrom, final Date dateTo);

    Date findDateToForProductionLine(final Date dateFrom, final long seconds, Entity productionLine);

    List<ShiftHour> getHoursForShift(final Entity shift, final Date dateFrom, final Date dateTo);

    /**
     * @deprecated use {@link Shift#worksAt(int)} instead.
     */
    @Deprecated
    boolean checkIfShiftWorkAtDate(final Date date, final Entity shift);

    String getWeekDayName(final DateTime dateTime);

    Optional<Shift> getShiftForNearestWorkingDate(DateTime nearestWorkingDate, Entity productionLine);

    Optional<DateTime> getNearestWorkingDate(final DateTime dateFrom, final Entity productionLine);

}
