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
package com.qcadoo.mes.technologies.tree;

import com.google.common.collect.Lists;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.states.constants.StateChangeStatus;
import com.qcadoo.mes.technologies.constants.OperationProductInComponentFields;
import com.qcadoo.mes.technologies.constants.OperationProductOutComponentFields;
import com.qcadoo.mes.technologies.constants.ProductStructureTreeNodeFields;
import com.qcadoo.mes.technologies.constants.TechnologiesConstants;
import com.qcadoo.mes.technologies.constants.TechnologyFields;
import com.qcadoo.mes.technologies.constants.TechnologyOperationComponentFields;
import com.qcadoo.mes.technologies.states.constants.TechnologyStateChangeFields;
import com.qcadoo.mes.technologies.states.constants.TechnologyStateStringValues;
import com.qcadoo.model.api.DataDefinition;
import com.qcadoo.model.api.DataDefinitionService;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.EntityList;
import com.qcadoo.model.api.EntityTree;
import com.qcadoo.model.api.search.JoinType;
import com.qcadoo.model.api.search.SearchOrders;
import com.qcadoo.model.api.search.SearchRestrictions;
import com.qcadoo.model.api.utils.EntityTreeUtilsService;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FormComponent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProductStructureTreeService {

    private static final String L_FINAL_PRODUCT = "finalProduct";

    private static final String L_INTERMEDIATE = "intermediate";

    private static final String L_COMPONENT = "component";

    public static final String L_MATERIAL = "material";

    @Autowired
    private DataDefinitionService dataDefinitionService;

    private Entity addChild(final List<Entity> tree, final Entity child, final Entity parent, final String entityType) {
        child.setField(ProductStructureTreeNodeFields.PARENT, parent);
        child.setField(ProductStructureTreeNodeFields.NUMBER, (long) tree.size() + 1);
        child.setField(ProductStructureTreeNodeFields.PRIORITY, 1);
        child.setField(ProductStructureTreeNodeFields.ENTITY_TYPE, entityType);

        Entity savedChild = child.getDataDefinition().save(child);

        tree.add(savedChild);
        return savedChild;
    }

    public Entity findOperationForProductAndTechnology(final Entity product, final Entity technology) {
        Entity operationProductOutComponent = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT).find()
                .createAlias(OperationProductOutComponentFields.OPERATION_COMPONENT, "c", JoinType.INNER)
                .add(SearchRestrictions.belongsTo("c." + TechnologyOperationComponentFields.TECHNOLOGY, technology))
                .add(SearchRestrictions.belongsTo(OperationProductInComponentFields.PRODUCT, product)).setMaxResults(1)
                .uniqueResult();
        if (operationProductOutComponent != null) {
            return operationProductOutComponent.getBelongsToField(OperationProductOutComponentFields.OPERATION_COMPONENT);
        } else {
            return null;
        }
    }

    public Entity findOperationForProductWithinChildren(final Entity product, final Entity toc) {
        Entity operationProductOutComponent = dataDefinitionService
                .get(TechnologiesConstants.PLUGIN_IDENTIFIER, TechnologiesConstants.MODEL_OPERATION_PRODUCT_OUT_COMPONENT).find()
                .createAlias(OperationProductOutComponentFields.OPERATION_COMPONENT, "c", JoinType.INNER)
                .add(SearchRestrictions.belongsTo("c." + TechnologyOperationComponentFields.PARENT, toc))
                .add(SearchRestrictions.belongsTo(OperationProductInComponentFields.PRODUCT, product)).setMaxResults(1)
                .uniqueResult();
        if (operationProductOutComponent != null) {
            return operationProductOutComponent.getBelongsToField(OperationProductOutComponentFields.OPERATION_COMPONENT);
        } else {
            return null;
        }
    }

    public Entity findTechnologyForProduct(final Entity product) {
        DataDefinition technologyDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_TECHNOLOGY);
        return technologyDD
                .find()
                .add(SearchRestrictions.isNull(TechnologyFields.TECHNOLOGY_TYPE))
                .add(SearchRestrictions.belongsTo(ProductStructureTreeNodeFields.PRODUCT, product))
                .add(SearchRestrictions.or(SearchRestrictions.eq(TechnologyFields.STATE, TechnologyStateStringValues.ACCEPTED),
                        SearchRestrictions.eq(TechnologyFields.STATE, TechnologyStateStringValues.CHECKED)))
                .addOrder(SearchOrders.desc(TechnologyFields.MASTER)).addOrder(SearchOrders.asc(TechnologyFields.NUMBER))
                .setMaxResults(1).uniqueResult();
    }

    private BigDecimal findQuantityOfProductInOperation(final Entity product, final Entity operation) {
        EntityList outProducts = operation.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_OUT_COMPONENTS);

        Entity productComponent = outProducts.find()
                .add(SearchRestrictions.belongsTo(ProductStructureTreeNodeFields.PRODUCT, product)).setMaxResults(1)
                .uniqueResult();
        if (productComponent != null) {
            return productComponent.getDecimalField(ProductStructureTreeNodeFields.QUANTITY);
        }
        EntityList inProducts = operation.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS);
        productComponent = inProducts.find().add(SearchRestrictions.belongsTo(ProductStructureTreeNodeFields.PRODUCT, product))
                .setMaxResults(1).uniqueResult();
        if (productComponent != null) {
            return productComponent.getDecimalField(ProductStructureTreeNodeFields.QUANTITY);
        }
        return null;
    }

    private void generateTreeForSubproducts(final Entity operation, final Entity technology, final List<Entity> tree,
            final Entity parent, final ViewDefinitionState view, final Entity mainTechnology) {
        EntityList productInComponents = operation
                .getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS);
        DataDefinition treeNodeDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_PRODUCT_STRUCTURE_TREE_NODE);
        for (Entity productInComp : productInComponents) {
            Entity child = treeNodeDD.create();
            Entity product = productInComp.getBelongsToField(OperationProductInComponentFields.PRODUCT);
            Entity subOperation = findOperationForProductWithinChildren(product, operation);
            BigDecimal quantity = findQuantityOfProductInOperation(product, operation);
            Entity subTechnology = findTechnologyForProduct(product);

            if (subTechnology != null) {
                if (subOperation == null) {
                    Entity operationForTechnology = findOperationForProductAndTechnology(product, subTechnology);
                    Entity technologyGroup = subTechnology.getBelongsToField(TechnologyFields.TECHNOLOGY_GROUP);
                    BigDecimal standardPerformanceTechnology = subTechnology
                            .getDecimalField(TechnologyFields.STANDARD_PERFORMANCE_TECHNOLOGY);

                    child.setField(ProductStructureTreeNodeFields.TECHNOLOGY, subTechnology);
                    child.setField(ProductStructureTreeNodeFields.MAIN_TECHNOLOGY, mainTechnology);
                    child.setField(ProductStructureTreeNodeFields.OPERATION, operationForTechnology);
                    child.setField(ProductStructureTreeNodeFields.PRODUCT, product);
                    child.setField(ProductStructureTreeNodeFields.QUANTITY, quantity);
                    child.setField(ProductStructureTreeNodeFields.DIVISION,
                            operationForTechnology.getBelongsToField(TechnologyOperationComponentFields.DIVISION));
                    child.setField(ProductStructureTreeNodeFields.TECHNOLOGY_GROUP, technologyGroup);
                    child.setField(ProductStructureTreeNodeFields.STANDARD_PERFORMANCE_TECHNOLOGY, standardPerformanceTechnology);
                    child = addChild(tree, child, parent, L_COMPONENT);
                    generateTreeForSubproducts(operationForTechnology, subTechnology, tree, child, view, mainTechnology);
                } else {
                    child.setField(ProductStructureTreeNodeFields.TECHNOLOGY, technology);
                    child.setField(ProductStructureTreeNodeFields.MAIN_TECHNOLOGY, mainTechnology);
                    child.setField(ProductStructureTreeNodeFields.PRODUCT, product);
                    child.setField(ProductStructureTreeNodeFields.QUANTITY, quantity);
                    child.setField(ProductStructureTreeNodeFields.OPERATION, subOperation);
                    child.setField(ProductStructureTreeNodeFields.DIVISION,
                            subOperation.getBelongsToField(TechnologyOperationComponentFields.DIVISION));
                    child = addChild(tree, child, parent, L_INTERMEDIATE);
                    if (view != null) {
                        FormComponent productStructureForm = (FormComponent) view.getComponentByReference("productStructureForm");
                        if (productStructureForm != null) {
                            productStructureForm
                                    .addMessage(
                                            "technologies.technologyDetails.window.productStructure.productStructureForm.technologyAndOperationExists",
                                            MessageType.INFO,
                                            false,
                                            product.getStringField(ProductFields.NUMBER) + " "
                                                    + product.getStringField(ProductFields.NAME));
                        }
                    }
                    generateTreeForSubproducts(subOperation, technology, tree, child, view, mainTechnology);
                }
            } else {
                Entity technologyGroup = technology.getBelongsToField(TechnologyFields.TECHNOLOGY_GROUP);
                BigDecimal standardPerformanceTechnology = technology
                        .getDecimalField(TechnologyFields.STANDARD_PERFORMANCE_TECHNOLOGY);
                child.setField(ProductStructureTreeNodeFields.TECHNOLOGY, technology);
                child.setField(ProductStructureTreeNodeFields.MAIN_TECHNOLOGY, mainTechnology);
                child.setField(ProductStructureTreeNodeFields.PRODUCT, product);
                child.setField(ProductStructureTreeNodeFields.QUANTITY, quantity);
                child.setField(ProductStructureTreeNodeFields.TECHNOLOGY_GROUP, technologyGroup);
                child.setField(ProductStructureTreeNodeFields.STANDARD_PERFORMANCE_TECHNOLOGY, standardPerformanceTechnology);

                if (subOperation != null) {
                    child.setField(ProductStructureTreeNodeFields.OPERATION, subOperation);
                    child.setField(ProductStructureTreeNodeFields.DIVISION,
                            subOperation.getBelongsToField(TechnologyOperationComponentFields.DIVISION));

                    child = addChild(tree, child, parent, L_INTERMEDIATE);
                    generateTreeForSubproducts(subOperation, technology, tree, child, view, mainTechnology);
                } else {
                    child.setField(ProductStructureTreeNodeFields.OPERATION, operation);
                    child.setField(ProductStructureTreeNodeFields.DIVISION,
                            operation.getBelongsToField(TechnologyOperationComponentFields.DIVISION));

                    addChild(tree, child, parent, L_MATERIAL);
                }
            }
        }
    }

    public EntityTree generateProductStructureTree(final ViewDefinitionState view, final Entity technology) {
        Entity product = technology.getBelongsToField(TechnologyFields.PRODUCT);
        Entity operation = findOperationForProductAndTechnology(product, technology);
        Entity technologyFromDB = technology.getDataDefinition().get(technology.getId());
        EntityTree tree = technologyFromDB.getTreeField(TechnologyFields.PRODUCT_STRUCTURE_TREE);
        if (tree.getRoot() != null) {
            Date productStructureCreateDate = tree.getRoot().getDateField(ProductStructureTreeNodeFields.CREATE_DATE);
            List<Entity> treeEntities = tree.find().list().getEntities();
            Entity technologyStateChange = getLastTechnologyStateChange(technologyFromDB);
            if (productStructureCreateDate.before(technologyStateChange.getDateField(TechnologyStateChangeFields.DATE_AND_TIME))
                    || checkSubTechnologiesSubstitution(treeEntities)
                    || checkIfSubTechnologiesChanged(operation, productStructureCreateDate)) {
                deleteProductStructureTree(treeEntities);
            } else {
                return tree;
            }
        }
        DataDefinition treeNodeDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_PRODUCT_STRUCTURE_TREE_NODE);
        Entity root = treeNodeDD.create();
        BigDecimal quantity = findQuantityOfProductInOperation(product, operation);
        Entity technologyGroup = technology.getBelongsToField(TechnologyFields.TECHNOLOGY_GROUP);
        BigDecimal standardPerformanceTechnology = technology.getDecimalField(TechnologyFields.STANDARD_PERFORMANCE_TECHNOLOGY);
        root.setField(ProductStructureTreeNodeFields.TECHNOLOGY, technology);
        root.setField(ProductStructureTreeNodeFields.MAIN_TECHNOLOGY, technology);
        root.setField(ProductStructureTreeNodeFields.PRODUCT, product);
        root.setField(ProductStructureTreeNodeFields.OPERATION, operation);
        root.setField(ProductStructureTreeNodeFields.QUANTITY, quantity);
        root.setField(ProductStructureTreeNodeFields.DIVISION,
                operation.getBelongsToField(TechnologyOperationComponentFields.DIVISION));
        root.setField(ProductStructureTreeNodeFields.TECHNOLOGY_GROUP, technologyGroup);
        root.setField(ProductStructureTreeNodeFields.STANDARD_PERFORMANCE_TECHNOLOGY, standardPerformanceTechnology);

        List<Entity> productStructureList = new ArrayList<>();
        root = addChild(productStructureList, root, null, L_FINAL_PRODUCT);

        generateTreeForSubproducts(operation, technology, productStructureList, root, view, technology);

        return EntityTreeUtilsService.getDetachedEntityTree(productStructureList);
    }

    private void deleteProductStructureTree(List<Entity> treeEntities) {
        for (Entity entity : treeEntities) {
            entity.getDataDefinition().delete(entity.getId());
        }
    }

    private boolean checkSubTechnologiesSubstitution(List<Entity> treeEntities) {
        for (Entity entity : treeEntities) {
            String entityType = entity.getStringField(ProductStructureTreeNodeFields.ENTITY_TYPE);
            if (entityType.equals(L_INTERMEDIATE) || entityType.equals(L_FINAL_PRODUCT)) {
                continue;
            }
            Entity product = entity.getBelongsToField(ProductStructureTreeNodeFields.PRODUCT);
            Entity newTechnology = findTechnologyForProduct(product);
            if (entityType.equals(L_MATERIAL) && newTechnology != null) {
                return true;
            } else if (entityType.equals(L_COMPONENT)) {
                Entity oldTechnology = entity.getBelongsToField(ProductStructureTreeNodeFields.TECHNOLOGY);
                if (oldTechnology != null && newTechnology == null || oldTechnology != null
                        && !oldTechnology.getId().equals(newTechnology.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkIfSubTechnologiesChanged(Entity operation, Date productStructureCreateDate) {
        for (Entity productInComp : operation.getHasManyField(TechnologyOperationComponentFields.OPERATION_PRODUCT_IN_COMPONENTS)) {
            Entity product = productInComp.getBelongsToField(OperationProductInComponentFields.PRODUCT);
            Entity subOperation = findOperationForProductWithinChildren(product, operation);
            Entity subTechnology = findTechnologyForProduct(product);

            if (subTechnology != null) {
                Entity technologyStateChange = getLastTechnologyStateChange(subTechnology);
                if (productStructureCreateDate.before(technologyStateChange
                        .getDateField(TechnologyStateChangeFields.DATE_AND_TIME))) {
                    return true;
                }
                if (subOperation == null) {
                    Entity operationForTechnology = findOperationForProductAndTechnology(product, subTechnology);
                    boolean changed = checkIfSubTechnologiesChanged(operationForTechnology, productStructureCreateDate);
                    if (changed) {
                        return true;
                    }
                } else {
                    boolean changed = checkIfSubTechnologiesChanged(subOperation, productStructureCreateDate);
                    if (changed) {
                        return true;
                    }
                }
            } else if (subOperation != null) {
                boolean changed = checkIfSubTechnologiesChanged(subOperation, productStructureCreateDate);
                if (changed) {
                    return true;
                }
            }
        }
        return false;
    }

    public Entity getLastTechnologyStateChange(Entity technology) {
        return technology.getHasManyField(TechnologyFields.STATE_CHANGES).find()
                .add(SearchRestrictions.eq(TechnologyStateChangeFields.STATUS, StateChangeStatus.SUCCESSFUL.getStringValue()))
                .addOrder(SearchOrders.desc(TechnologyStateChangeFields.DATE_AND_TIME)).setMaxResults(1).uniqueResult();
    }

    public Entity getTechnologyAcceptStateChange(Entity technology) {
        return technology.getHasManyField(TechnologyFields.STATE_CHANGES).find()
                .add(SearchRestrictions.eq(TechnologyStateChangeFields.TARGET_STATE, TechnologyStateStringValues.ACCEPTED))
                .add(SearchRestrictions.eq(TechnologyStateChangeFields.STATUS, StateChangeStatus.SUCCESSFUL.getStringValue()))
                .addOrder(SearchOrders.desc(TechnologyStateChangeFields.DATE_AND_TIME)).setMaxResults(1).uniqueResult();
    }

    public Entity getTechnologyOutdatedStateChange(Entity technology) {
        return technology.getHasManyField(TechnologyFields.STATE_CHANGES).find()
                .add(SearchRestrictions.eq(TechnologyStateChangeFields.TARGET_STATE, TechnologyStateStringValues.OUTDATED))
                .add(SearchRestrictions.eq(TechnologyStateChangeFields.STATUS, StateChangeStatus.SUCCESSFUL.getStringValue()))
                .addOrder(SearchOrders.desc(TechnologyStateChangeFields.DATE_AND_TIME)).setMaxResults(1).uniqueResult();
    }

    public EntityTree getOperationComponentsFromTechnology(final Entity technology) {
        EntityTree productStructureTree = generateProductStructureTree(null, technology);
        return transformProductStructureTreeToTOCTree(productStructureTree);
    }

    private EntityTree transformProductStructureTreeToTOCTree(final EntityTree productStructureTree) {
        List<Entity> tocTree = Lists.newArrayList();
        DataDefinition tocDD = dataDefinitionService.get(TechnologiesConstants.PLUGIN_IDENTIFIER,
                TechnologiesConstants.MODEL_TECHNOLOGY_OPERATION_COMPONENT);
        Entity root = productStructureTree.getRoot();
        Long rootTocID = root.getBelongsToField(ProductStructureTreeNodeFields.OPERATION).getId();
        addChildTOC(tocTree, tocDD.get(rootTocID), null, root.getBelongsToField(ProductStructureTreeNodeFields.PRODUCT),
                L_FINAL_PRODUCT);
        addTocChildes(tocTree, tocDD, root);
        return EntityTreeUtilsService.getDetachedEntityTree(tocTree);
    }

    private void addTocChildes(List<Entity> tocTree, DataDefinition tocDD, Entity root) {
        Entity parent;
        for (Entity node : root.getHasManyField(TechnologyOperationComponentFields.CHILDREN)) {
            String entityType = node.getStringField(ProductStructureTreeNodeFields.ENTITY_TYPE);
            if (!entityType.equals(L_MATERIAL) && !entityType.equals(L_FINAL_PRODUCT)) {
                Long tocId = node.getBelongsToField(ProductStructureTreeNodeFields.OPERATION).getId();
                Entity toc = tocDD.get(tocId);
                Long parentId = node.getBelongsToField(ProductStructureTreeNodeFields.PARENT) != null ? node
                        .getBelongsToField(ProductStructureTreeNodeFields.PARENT)
                        .getBelongsToField(ProductStructureTreeNodeFields.OPERATION).getId() : node.getBelongsToField(
                        ProductStructureTreeNodeFields.OPERATION).getId();
                parent = getEntityById(tocTree, parentId);
                if(parent == null) {
                    parent.getId();
                }
                addChildTOC(tocTree, toc, parent, node.getBelongsToField(ProductStructureTreeNodeFields.PRODUCT), entityType);
            }
            addTocChildes(tocTree, tocDD, node);
        }
    }

    private Entity getEntityById(final List<Entity> tree, final Long id) {
        for (Entity entity : tree) {
            if (entity.getId().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    private void addChildTOC(final List<Entity> tree, final Entity child, final Entity parent, final Entity product, String type) {
        child.setField(TechnologyOperationComponentFields.PARENT, parent);
        child.setField(TechnologyOperationComponentFields.PRIORITY, 1);
        child.setField(TechnologyOperationComponentFields.TYPE_FROM_STRUCTURE_TREE, type);
        child.setField(TechnologyOperationComponentFields.PRODUCT_FROM_STRUCTURE_TREE, product);
        if (parent != null) {
            List<Entity> children = Lists.newArrayList();
            EntityList tocChildren = parent.getHasManyField(TechnologyOperationComponentFields.CHILDREN);
            if (!tocChildren.isEmpty()) {
                children = Lists.newArrayList(tocChildren);
            }
            if (tocChildren.stream().noneMatch(e -> e.getId().equals(child.getId()))) {
                children.add(child);
            }
            parent.setField(TechnologyOperationComponentFields.CHILDREN, children);
        }
        tree.add(child);
    }
}
