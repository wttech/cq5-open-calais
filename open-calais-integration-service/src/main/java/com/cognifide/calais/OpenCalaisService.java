package com.cognifide.calais;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.service.component.ComponentContext;
import org.slf4j.LoggerFactory;

import com.day.cq.tagging.InvalidTagFormatException;
import com.day.cq.tagging.Tag;
import com.day.cq.tagging.TagManager;

@Component( immediate = true, metatype = true, label = "OpenCalais Service", description = "OpenCalais Service allows to automatically tag the content." )
@Service
@Properties({ 
	@Property(
		name="api_url",
		value="http://api.opencalais.com/tag/rs/enrich",
		label="API Url",
		description="Calais service url"),	
	@Property(
		name="api_key",
		value="<api key here>",
		label="API Key",
		description="Calais service key"),
	@Property(
		name="allow_distribution",
		label="Allow distribution", 
		boolValue=false,
		description="Indicates whether the extracted metadata can be distributed"),
	@Property(
		name="allow_search",
		label="Allow search",
		boolValue=false,
		description="Indicates whether future searches can be performed on the extracted metadata"),
	@Property(
		name = "content_fields",
		value = { "jcr:title", "jcr:description", "text" },
		cardinality = Integer.MAX_VALUE,
		label = "Content fields",
		description = "List of fields to grab content from")			
})			
public class OpenCalaisService implements CalaisService {
	
	private final static String TAG_NAMESPACE = "Calais";

	private final static String DEBUG_PREFIX = "[CALAIS] ";
	
	private String apiUrl = "";
	
    private String apiKey = "";        
	
    private Boolean allowDistribution;    
	
    private Boolean allowSearch;
	
    private String[] contentFields;
    
	/**
	 * Method is ran by Felix on every handler activation or configuration
	 * change. It assures that needed variables are up to date.
	 * 
	 * @param componentContext
	 *            is an object that holds handler configuration/properties
	 */
	protected void activate(ComponentContext componentContext) {
		apiUrl = componentContext.getProperties().get("api_url").toString();
		apiKey = componentContext.getProperties().get("api_key").toString();		
		allowDistribution = (Boolean)componentContext.getProperties().get("allow_distribution");
		allowSearch = (Boolean)componentContext.getProperties().get("allow_search");		
		contentFields = OsgiUtil.toStringArray(componentContext.getProperties().get("content_fields"));		
		log(String.format("OpenCalais service activated, url: %s", apiUrl));
	}
	
	private void log(String msg) {
		LoggerFactory.getLogger(getClass()).warn(DEBUG_PREFIX + msg);
	}
	
    public void tagContent(Resource resource) throws Exception {
    	// extract content from given resource
    	String content = getContent(resource);
    	// analyze and extract tags from content
    	Map<String, List<String>> tags = getTags(content);
    	// update taxonomy and apply tags on resource
    	setTags(resource, tags);    	
    }
    
	private String getContent(Resource res) {
    	StringBuilder builder = new StringBuilder();
		ValueMap vm = res.adaptTo(ValueMap.class);
		
		for(String field : contentFields) {
			if(vm.containsKey(field)) {
				String value = vm.get(field, "");
				if(!value.isEmpty()) {
					builder.append(value + "\n");					
				}
			}
		}
		
		Iterator<Resource> resIterator = res.listChildren();
		while(resIterator.hasNext()) {
			Resource current = resIterator.next();
			builder.append(getContent(current));
		}						
		return builder.toString();
    }    
	
	private Map<String, List<String>> getTags(String content) throws IOException, JSONException {		
		String response = doCalaisRequest(content);
		return getTagsFromResponse(response);
	}
	
	@SuppressWarnings("deprecation")
	private String doCalaisRequest(String textBody) throws IOException {
		HttpClient client = new HttpClient();
		client.getParams().setParameter("http.useragent", "Calais Rest Client");
		
		PostMethod method = createPostMethod();
		method.setRequestEntity(new StringRequestEntity(textBody));
	
		try {
            int returnCode = client.executeMethod(method);
            if (returnCode == HttpStatus.SC_NOT_IMPLEMENTED) {
                log("The Post method is not implemented by this URI");
                return method.getResponseBodyAsString();
            } else if (returnCode == HttpStatus.SC_OK) {
                return getResponse(method);
            } else {
                log("Got code: " + returnCode);
                log("response: " + method.getResponseBodyAsString());
            }
        } finally {
            method.releaseConnection();
        }
        
        return "";
    }	
	
	private PostMethod createPostMethod() {
        PostMethod method = new PostMethod(apiUrl);
        // Set mandatory parameters
        method.setRequestHeader("x-calais-licenseID", apiKey);
        // Set input content type
        method.setRequestHeader("Content-Type", "text/raw; charset=UTF-8");
		// Set response/output format
        method.setRequestHeader("Accept", "application/json");
        // Enable Social Tags processing
        method.setRequestHeader("enableMetadataType", "SocialTags");
        // other flags        
        method.setRequestHeader("allowDistribution", allowDistribution.toString().toLowerCase());
        method.setRequestHeader("allowSearch", allowSearch.toString().toLowerCase());
        return method;
    }
	
    private String getResponse(PostMethod method) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8"));
        StringWriter writer = new StringWriter();
        String responseString = "";
        String line;
        while ((line = reader.readLine()) != null) {
            writer.write(line);
        }        
        responseString = writer.toString();
        return responseString;
    }	
	
	private Map<String, List<String>> getTagsFromResponse(String response) throws JSONException {
		Map<String, List<String>> ret = new HashMap<String, List<String>>();		

       	JSONObject json = new JSONObject(response);
       	Iterator<String> i = json.keys();
       	while(i.hasNext()) {
       		String key = (String)i.next();
       		if(key != null) {
       			JSONObject item = (JSONObject)json.get(key);
       			String typeGroup = item.getString("_typeGroup");        			

       			if(typeGroup != null && typeGroup.equals("entities")) {
       				String type = item.getString("_type");
       				String name = item.getString("name");
       				//Double relevance = item.getDouble("relevance");
       				//int count = item.getJSONArray("instances").length();
        				
       				if(!ret.containsKey(type)) {
       					ret.put(type, new ArrayList<String>());
       				}
       				ret.get(type).add(name);
        		}
        	}
       	}
        return ret;
	}	
        
    private void setTags(Resource resource, Map<String, List<String>> tags) throws InvalidTagFormatException {    	
    	ResourceResolver resolver = resource.getResourceResolver(); 
    	TagManager tagManager = resolver.adaptTo(TagManager.class);
    	    	
		// update taxonomy
		List<Tag> tagsList = new ArrayList<Tag>();			
		for(String tagGroupName : tags.keySet()) {
			for(String tagName : tags.get(tagGroupName)) {
		    		Tag tag = tagManager.createTag(TAG_NAMESPACE + ":" + tagGroupName + "/" + tagName, tagName, "Calais auto tag");
		    		tagsList.add(tag);
			}
		}
		
		// apply tags on resource 			
		tagManager.setTags(resource, tagsList.toArray(new Tag[]{}));    			
	}    
}
