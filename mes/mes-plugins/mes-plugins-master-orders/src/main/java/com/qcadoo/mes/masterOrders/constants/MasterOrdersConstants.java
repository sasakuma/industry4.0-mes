/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 * <p/>
 * This file is part of Qcadoo.
 * <p/>
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.masterOrders.constants;

public final class MasterOrdersConstants {

    private MasterOrdersConstants() {

    }

    public static final String[] FILE_EXTENSIONS = new String[] { "bmp", "gif", "jpg", "jpeg", "png", "tiff", "wmf", "eps" };

    public static final String PLUGIN_IDENTIFIER = "masterOrders";

    public static final String MODEL_MASTER_ORDER = "masterOrder";

    public static final String MODEL_MASTER_ORDER_DEFINITION = "masterOrderDefinition";

    public static final String MODEL_MASTER_ORDER_PRODUCT = "masterOrderProduct";

    public static final String MODEL_MASTER_ORDER_POSITION_DTO = "masterOrderPositionDto";

    public static final String MODEL_MASTER_ORDER_DTO = "masterOrderDto";

    public static String masterOrderDetailsUrl(Long id) {

        return "#page/" + PLUGIN_IDENTIFIER + "/" + MODEL_MASTER_ORDER + "Details.html?context=%7B%22form.id%22%3A%22" + id
                + "%22%2C%22form.undefined%22%3Anull%7D";
    }
}
