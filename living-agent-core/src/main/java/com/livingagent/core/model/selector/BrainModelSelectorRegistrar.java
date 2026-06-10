package com.livingagent.core.model.selector;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BrainModelSelectorRegistrar {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BrainModelSelectorManager manager;
    private final TechBrainModelSelector techBrainModelSelector;
    private final AdminBrainModelSelector adminBrainModelSelector;
    private final HrBrainModelSelector hrBrainModelSelector;
    private final FinanceBrainModelSelector financeBrainModelSelector;
    private final SalesBrainModelSelector salesBrainModelSelector;
    private final OpsBrainModelSelector opsBrainModelSelector;
    private final LegalBrainModelSelector legalBrainModelSelector;
    private final CsBrainModelSelector csBrainModelSelector;

    public BrainModelSelectorRegistrar(
        BrainModelSelectorManager manager,
        TechBrainModelSelector techBrainModelSelector,
        AdminBrainModelSelector adminBrainModelSelector,
        HrBrainModelSelector hrBrainModelSelector,
        FinanceBrainModelSelector financeBrainModelSelector,
        SalesBrainModelSelector salesBrainModelSelector,
        OpsBrainModelSelector opsBrainModelSelector,
        LegalBrainModelSelector legalBrainModelSelector,
        CsBrainModelSelector csBrainModelSelector
    ) {
        this.manager = manager;
        this.techBrainModelSelector = techBrainModelSelector;
        this.adminBrainModelSelector = adminBrainModelSelector;
        this.hrBrainModelSelector = hrBrainModelSelector;
        this.financeBrainModelSelector = financeBrainModelSelector;
        this.salesBrainModelSelector = salesBrainModelSelector;
        this.opsBrainModelSelector = opsBrainModelSelector;
        this.legalBrainModelSelector = legalBrainModelSelector;
        this.csBrainModelSelector = csBrainModelSelector;
    }

    @PostConstruct
    public void registerAllSelectors() {
        manager.register(techBrainModelSelector);
        manager.register(adminBrainModelSelector);
        manager.register(hrBrainModelSelector);
        manager.register(financeBrainModelSelector);
        manager.register(salesBrainModelSelector);
        manager.register(opsBrainModelSelector);
        manager.register(legalBrainModelSelector);
        manager.register(csBrainModelSelector);

        log.info("All brain model selectors registered: {} brains configured", manager.getAllConfigs().size());
    }
}
