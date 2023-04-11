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
package com.qcadoo.mes.technologies.grouping;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.qcadoo.mes.technologies.constants.OperationProductInComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentMergeProductFields;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.search.JoinType;
import com.qcadoo.model.api.search.SearchProjections;
import com.qcadoo.model.api.search.SearchRestrictions;

@Service
public class OperationMergeServiceImpl implements OperationMergeService {

    private DataDefinitionService dataDefinitionService;

    @Autowired
    public OperationMergeServiceImpl(DataDefinitionService dataDefinitionService) {
        this.dataDefinitionService = dataDefinitionService;
    }

    @Override
    public void mergeProductIn(Entity order, Entity existingOperationComponent, Entity operationProductIn, BigDecimal quantity) {
        mergeProduct(mergesProductInDD(), order, existingOperationComponent, operationProductIn, quantity);
    }

    @Override
    public void storeProductIn(Entity order, Entity operationComponent, Entity mergeOperationComponent, Entity operationProductIn, BigDecimal quantity) {
        persistMerge(mergesProductInDD(), order, operationComponent, mergeOperationComponent, operationProductIn, quantity);
    }

    @Override
    public void mergeProductOut(Entity order, Entity existingOperationComponent, Entity operationProductOut, BigDecimal quantity) {
        mergeProduct(mergesProductOutDD(), order, existingOperationComponent, operationProductOut, quantity);
    }

    @Override
    public void storeProductOut(Entity order, Entity operationComponent, Entity mergeOperationComponent, Entity operationProductIn, BigDecimal quantity) {
        persistMerge(mergesProductOutDD(), order, operationComponent, mergeOperationComponent, operationProductIn, quantity);
    }

    @Override
    public List<Entity> findMergedProductInByOrder(Entity order) {
        return mergesProductInDD().find().add(SearchRestrictions.belongsTo("order", order)).list().getEntities();
    }

    @Override
    public List<Entity> findMergedProductOutByOrder(Entity order) {
        return mergesProductOutDD().find().add(SearchRestrictions.belongsTo("order", order)).list().getEntities();
    }

    @Override
    public List<Long> findMergedToOperationComponentIds() {
        List<Entity> entities = mergesProductInDD().find()
                .add(SearchRestrictions.lt(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE, BigDecimal.ZERO))
                .setProjection(SearchProjections.field(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT))
                .list().getEntities();
        return Lists.newArrayList(Collections2.transform(entities, new Function<Entity, Long>() {
            @Override
            public Long apply(final Entity from) {
                return from.getId();
            }
        }));
    }

    @Override
    public Entity findMergedByOperationComponent(Entity operationComponent) {
        return mergesProductInDD().find()
                .add(SearchRestrictions.and(
                        SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent),
                        SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT, operationComponent)))
                .uniqueResult();
    }

    @Override
    public Entity findMergedByMergedToc(Entity toc) {
        return mergesProductInDD().find()
                .add(SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT, toc)).setMaxResults(1).uniqueResult();
    }

    @Override
    public Entity findMergedByMergedTocId(Long tocId) {
        return mergesProductInDD().find()
                .add(SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT + ".id", tocId)).setMaxResults(1).uniqueResult();
    }

    @Override
    public boolean isMergedByTocId(Long tocId) {
        return mergesProductInDD().find()
                .add(SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT + ".id", tocId))
                .add(SearchRestrictions.lt(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE, BigDecimal.ZERO))
                .list()
                .getTotalNumberOfEntities() > 0;
    }

    @Override
    public Entity findMergedFromOperationInByOperationComponentId(Long operationComponentId) {
        Entity operationComponent = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT).get(operationComponentId);
        return mergesProductInDD().find()
                .add(SearchRestrictions.and(
                        SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent),
                        SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT, operationComponent)))
                .uniqueResult();
    }

    @Override
    public Entity findMergedFromOperationOutByOperationComponentId(Long operationComponentId) {
        return mergesProductOutDD().find()
                .add(SearchRestrictions.and(
                        SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT + ".id", operationComponentId),
                        SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT + ".id", operationComponentId)))
                .uniqueResult();
    }

    @Override
    public List<Entity> findMergedToByOperationComponentId(Long operationComponentId) {
        Entity operationComponent = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT).get(operationComponentId);
        return mergesProductInDD().find()
                .add(SearchRestrictions.and(
                        SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent),
                        SearchRestrictions.not(
                                SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT, operationComponent)
                        )))
                .list().getEntities();
    }

    @Override
    public List<Entity> findMergedProductInComponentsByOperationComponent(Entity operationComponent) {
        return mergesProductInDD().find()
                .add(SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent))
                .add(SearchRestrictions.gt(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE, BigDecimal.ZERO))
                .list().getEntities();
    }

    @Override
    public List<Entity> findMergedToProductOutComponentsByOperationComponent(Entity operationComponent) {
        return mergesProductOutDD().find()
                .add(SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent))
                .add(SearchRestrictions.isNotNull(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE))
                .list().getEntities();
    }

    @Override
    public List<Entity> findMergedEntitiesByOperationComponent(Entity operationComponent) {
        return mergesProductOutDD().find()
                .add(SearchRestrictions.belongsTo(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent))
                .add(SearchRestrictions.isNull(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE))
                .list().getEntities();
    }

    @Override
    public List<Entity> findMergedEntitiesByOperationComponentId(Long operationComponentId) {
        return mergesProductOutDD().find()
                .add(SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT + ".id", operationComponentId))
                .add(SearchRestrictions.isNull(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE))
                .list().getEntities();
    }

    @Override
    public void adjustOperationProductComponentsDueMerge(Entity operationComponent) {
        adjustOperationProductInComponentsDueMerge(operationComponent);
        adjustOperationProductOutComponentsDueMerge(operationComponent);
    }

    private void adjustOperationProductInComponentsDueMerge(Entity operationComponent) {
        Entity merge = findMergedByOperationComponent(operationComponent);

        List<Entity> operationProductInComponents = operationProductInComponents(operationComponent);
        List<Entity> operationProductInComponentsNew = Lists.newArrayList();

        BigDecimal quantity = merge.getDecimalField(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE);
        for (Entity operationProductInComponent : operationProductInComponents) {
            if (operationProductInComponent.getId().equals(mergedOperationProductComponent(merge).getId())) {
                operationProductInComponent.setField(OperationProductInComponentFields.QUANTITY, quantity);
                operationProductInComponentsNew.add(operationProductInComponent);
            }
        }

        operationComponent.setField(TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS, operationProductInComponentsNew);
    }

    private List<Entity> operationProductInComponents(Entity operationComponent) {
        return operationComponent.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS);
    }

    private void adjustOperationProductOutComponentsDueMerge(Entity operationComponent) {
        Entity merge = findMergedByOperationComponent(operationComponent);

        List<Entity> operationProductOutComponents = operationProductOutComponents(operationComponent);
        List<Entity> operationProductOutComponentsNew = Lists.newArrayList();

        BigDecimal quantity = merge.getDecimalField(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE);
        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
            boolean modifiedExisting = false;
            for (Entity operationProductOutComponent : operationProductOutComponents) {
                if (operationProductOutComponent.getId().equals(mergedOperationProductComponent(merge).getId())) {
                    operationProductOutComponent.setField(OperationProductInComponentFields.QUANTITY, quantity);
                    operationProductOutComponentsNew.add(operationProductOutComponent);
                    modifiedExisting = true;
                }
            }

            if (!modifiedExisting) {
                Entity mergedOperationComponent = mergedOperationProductComponent(merge);
                operationProductOutComponentsNew.add(mergedOperationComponent);
            }
        }

        operationComponent.setField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS, operationProductOutComponentsNew);
    }

    private Entity mergedOperationProductComponent(Entity merge) {
        return merge.getBelongsToField(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_PRODUCT_COMPONENT);
    }

    private List<Entity> operationProductOutComponents(Entity operationComponent) {
        return operationComponent.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS);
    }

    private void mergeProduct(DataDefinition dataDefinition, Entity order, Entity existingOperationComponent, Entity operationProduct, BigDecimal quantity) {
        Entity alreadyMergedProductComponentForOperation = findAlreadyMergedProductComponentForOperation(dataDefinition, existingOperationComponent, operationProduct);
        if (alreadyMergedProductComponentForOperation != null) {
            alreadyMergedProductComponentForOperation.setField(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE, quantity);
            dataDefinition.save(alreadyMergedProductComponentForOperation);
        } else {
            persistMerge(dataDefinition, order, existingOperationComponent, existingOperationComponent, operationProduct, quantity);
        }
    }

    private void persistMerge(DataDefinition dataDefinition, Entity order, Entity operationComponent, Entity mergeOperationComponent, Entity operationProduct, BigDecimal quantity) {
        Entity merge = dataDefinition.create();
        merge.setField("order", order);
        merge.setField(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT, operationComponent);
        merge.setField(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT, mergeOperationComponent);
        merge.setField(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_PRODUCT_COMPONENT, operationProduct);
        merge.setField(TechnologyOperationComponentMergeProductFields.QUANTITY_CHANGE, quantity);
        dataDefinition.save(merge);
    }

    private Entity findAlreadyMergedProductComponentForOperation(DataDefinition dataDefinition, Entity existingOperationComponent, Entity operationProductIn) {
        Entity product = operationProductIn.getBelongsToField(OperationProductInComponentFields.PRODUCT);
        return dataDefinition.find().createAlias(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_PRODUCT_COMPONENT, "mopc", JoinType.FULL)
                .add(SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.OPERATION_COMPONENT + ".id", existingOperationComponent.getId()))
                .add(SearchRestrictions.eq(TechnologyOperationComponentMergeProductFields.MERGED_OPERATION_COMPONENT + ".id", existingOperationComponent.getId()))
                .add(SearchRestrictions.eq("mopc.product.id", product.getId())).setMaxResults(1).uniqueResult();
    }

    private DataDefinition mergesProductInDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT_MERGE_PRODUCT_IN);
    }

    private DataDefinition mergesProductOutDD() {
        return dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT_MERGE_PRODUCT_OUT);
    }

}
