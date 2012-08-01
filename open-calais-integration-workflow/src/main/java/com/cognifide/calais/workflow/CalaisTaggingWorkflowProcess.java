package com.cognifide.calais.workflow;

import java.util.HashMap;
import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import com.cognifide.calais.CalaisService;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;

/**
 * @author Mateusz Kula
 */
@Component(immediate=true, metatype=false)
@Service(value=WorkflowProcess.class)
@Properties({ @Property(name = "process.label", value = "OpenCalais tagging step") })
public class CalaisTaggingWorkflowProcess implements WorkflowProcess {
	
	@Reference
	private ResourceResolverFactory resolverFactory;
	
	@Reference
	private CalaisService openCalaisService;	
	
	@Override
	public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {

	    final Map<String, Object> map = new HashMap<String, Object>(); 
	    map.put( "user.jcr.session", workflowSession.getSession());
	    
	    try {
			ResourceResolver resolver = resolverFactory.getResourceResolver(map);
			
			String payload = (String)workItem.getWorkflowData().getPayload();
			Resource pageResource = resolver.getResource(payload);
			Resource contentResource = pageResource.getChild("jcr:content");
			
			// extract content from page
			openCalaisService.tagContent(contentResource);

		} catch (Exception e) {
			throw new WorkflowException("Failed to tag " + workItem.getId(), e);
		}	    
	}
}
