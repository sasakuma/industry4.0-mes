/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo Framework
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
package com.qcadoo.mes.productionBalancePerShift.domain;

import junit.framework.Assert;

import org.joda.time.LocalDate;
import org.junit.Test;

public class ProductionProgressScopeTest {

    @Test
    public void shouldObeyValuesMandatoryContract() {
        // given
        LocalDate someDay = LocalDate.now();
        Order someOrder = new Order(1L, "ord-1");
        Operation someOperation = new Operation(1L, "op1", "1.A.2.");
        Shift someShift = new Shift(3L, "Zmiana 3");
        Product someProduct = new Product(1L, "prod-1", "kg");

        try {
            new ProductionProgressScope(someDay, someOrder, someOperation, someShift, someProduct);
        } catch (IllegalArgumentException ignored) {
            Assert.fail("Preconditions shouldn't invalidate not null arguments");
        }
        performConstructorArgumentsMandatoryCheck(null, someOrder, someOperation, someShift, someProduct);
        performConstructorArgumentsMandatoryCheck(someDay, null, someOperation, someShift, someProduct);
        performConstructorArgumentsMandatoryCheck(someDay, someOrder, null, someShift, someProduct);
        performConstructorArgumentsMandatoryCheck(someDay, someOrder, someOperation, null, someProduct);
        performConstructorArgumentsMandatoryCheck(someDay, someOrder, someOperation, someShift, null);
    }

    private void performConstructorArgumentsMandatoryCheck(final LocalDate day, final Order order, final Operation operation,
            final Shift shift, final Product product) {
        try {
            new ProductionProgressScope(day, order, operation, shift, product);
            Assert.fail();
        } catch (IllegalArgumentException ignored) {
            // Success
        }
    }

}
