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
package com.qcadoo.mes.productionLines.listeners;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.constants.BasicConstants;
import com.qcadoo.mes.basic.constants.DivisionFields;
import com.qcadoo.mes.basic.constants.WorkstationFields;
import com.qcadoo.mes.productionLines.constants.WorkstationFieldsPL;
import com.qcadoo.mes.productionLines.factoryStructure.FactoryStructureGenerationService;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityTree;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.GridComponent;
import com.qcadoo.view.api.components.WindowComponent;

@Service
public class ProductionLineDetailsListeners {

    @Autowired
    private DataDefinitionService dataDefinitionService;

    @Autowired
    private FactoryStructureGenerationService factoryStructureGenerationService;

    public void onAddExistingEntity(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        if (args.length < 1) {
            return;
        }
        FormComponent form = (FormComponent) view.getComponentByReference("form");
        Entity productionLine = form.getPersistedEntityWithIncludedFormValues();
        List<Long> addedWorkstationIds = parseIds(args[0]);
        for (Long addedWorkstationId : addedWorkstationIds) {
            Entity workstation = dataDefinitionService.get(BasicConstants.PLUGIN_IDENTIFIER, BasicConstants.MODEL_WORKSTATION)
                    .get(addedWorkstationId);
            workstation.setField(WorkstationFieldsPL.PRODUCTION_LINE, productionLine);
            workstation.getDataDefinition().save(workstation);
        }
    }

    private List<Long> parseIds(final String ids) {
        List<Long> result = Lists.newArrayList();
        String[] splittedIds = ids.replace("[", "").replace("]", "").replace("\"", "").split(",");
        for (int i = 0; i < splittedIds.length; i++) {
            result.add(Long.parseLong(splittedIds[i]));
        }
        return result;
    }

    public void onRemoveSelectedEntity(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        GridComponent workstationsGrid = (GridComponent) view.getComponentByReference("workstations");
        List<Entity> workstationsToDelete = workstationsGrid.getSelectedEntities();
        for (Entity workstation : workstationsToDelete) {
            workstation.setField(WorkstationFieldsPL.PRODUCTION_LINE, null);
            workstation.setField(WorkstationFields.DIVISION, null);
            workstation.getDataDefinition().save(workstation);

        }
    }

    public void onRemoveSelectedDivisions(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        GridComponent divisionsGrid = (GridComponent) view.getComponentByReference("divisions");
        List<Entity> divisionsToDelete = divisionsGrid.getSelectedEntities();
        FormComponent form = (FormComponent) view.getComponentByReference("form");
        Long productionLineId = form.getEntityId();
        for (Entity division : divisionsToDelete) {
            List<Entity> workstations = division.getHasManyField(DivisionFields.WORKSTATIONS);
            workstations
                    .stream()
                    .filter(workstation -> workstation.getBelongsToField(WorkstationFieldsPL.PRODUCTION_LINE).getId()
                            .equals(productionLineId)).forEach(workstation -> {
                        workstation.setField(WorkstationFieldsPL.PRODUCTION_LINE, null);
                        workstation.setField(WorkstationFields.DIVISION, null);
                        workstation.getDataDefinition().save(workstation);
                    });
        }
    }

    public void generateFactoryStructure(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent form = (FormComponent) view.getComponentByReference("form");
        Entity productionLine = form.getEntity();
        EntityTree structure = factoryStructureGenerationService.generateFactoryStructureForProductionLine(productionLine);
        productionLine.setField(WorkstationFieldsPL.FACTORY_STRUCTURE, structure);
        form.setEntity(productionLine);
        WindowComponent window = (WindowComponent) view.getComponentByReference("window");
        window.setActiveTab("factoryStructureTab");
    }

}
