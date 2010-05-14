package edu.utdallas.hadooprdf.query.jobrunner;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import edu.utdallas.hadooprdf.data.metadata.DataSet;
import edu.utdallas.hadooprdf.data.rdf.uri.prefix.PrefixNamespaceTree;
import edu.utdallas.hadooprdf.query.generator.job.JobPlan;

/**
 * The generic reducer class for a SPARQL query
 * @author vaibhav
 *
 */
public class GenericReducer extends Reducer<Text, Text, Text, Text>
{		
	private JobPlan jp = null;
	private PrefixNamespaceTree prefixTree = null;
	
	@Override
	protected void setup(Context context) throws IOException,
	InterruptedException 
	{
		try
		{
			org.apache.hadoop.conf.Configuration hadoopConfiguration = context.getConfiguration(); 
			FileSystem fs = FileSystem.get( hadoopConfiguration );
			DataSet ds = new DataSet( new Path( hadoopConfiguration.get( "dataset" ) ), hadoopConfiguration );
			ObjectInputStream objstream = new ObjectInputStream( fs.open( new Path( ds.getPathToTemp(), "job.txt" ) ) );
			this.jp = (JobPlan)objstream.readObject();
			objstream.close();
			prefixTree = ds.getPrefixNamespaceTree();
		}
		catch( Exception e ) { throw new InterruptedException(e.getMessage()); }//e.printStackTrace(); }
	}
	
	/**
	 * The reduce method
	 * @param key - the input key
	 * @param value - the input value
	 * @param context - the context
	 */
	@Override
	public void reduce( Text key, Iterable<Text> value, Context context ) throws IOException, InterruptedException
	{
		int count = 0;
        String sValue = "";
        
        //Iterate over all values for a particular key
        Iterator<Text> iter = value.iterator();
        while ( iter.hasNext() ) 
        {
        	if( !jp.getHasMoreJobs() )
        		count++;
            sValue += iter.next().toString() + '\t';
        }
        
        //TODO: How to find the order of results with the given query, may need rearranging of value here
        //TODO: Sometimes only the key is the result, sometimes the key and part of the value is the result, how to find this out ??
        //Write the result
        if( !jp.getHasMoreJobs() )
        {
        	//Single variable in the query, e.g. ?X
        	//or multiple variables e.g. ?X ?Y
        	if( jp.getTotalVariables() == 1 ) 
        	{
        		if( count == jp.getVarTrPatternCount( jp.getJoiningVariablesList().get( 0 ) ) )
        		{
        			String keyString = key.toString().substring( jp.getJoiningVariablesList().get( 0 ).substring( 1 ).length() );
        			String prefix = keyString.substring( 0, keyString.indexOf( "#" ) + 1 );
        			String namespace = prefixTree.matchAndReplaceNamespace( prefix );
        			context.write( new Text( namespace + keyString.substring( keyString.indexOf( "#" ) + 1, keyString.length() ) ), new Text( "" ) );
        		}
        	}
        	else
        	{
        		if( jp.getJoiningVariablesList().size() == 1 && count == jp.getVarTrPatternCount( jp.getJoiningVariablesList().get( 0 ) ) )
        		{
        			Iterator<String> iterVars = jp.getSelectClauseVarList().iterator();
        			Map<String,String> vars = new TreeMap<String,String>();
        			while( iterVars.hasNext() )
        			{
        				String variable = iterVars.next();
        				vars.put( variable, variable );
        			}
        			
        			String keyString = key.toString();
        			String[] splitKey = keyString.split( "#" );
        			String varKey = splitKey[0];
        			String prefixKey = ""; if( splitKey.length > 2 ) prefixKey = splitKey[1] + "#";
        			String namespaceKey = prefixTree.matchAndReplaceNamespace( prefixKey );
        			if( splitKey.length > 2 ) vars.put( varKey, namespaceKey + splitKey[2] );
        			else vars.put( varKey,  namespaceKey + splitKey[1] );
        			
        			String[] splitRes = sValue.split( "\t" );
        			for( int j = 0; j < splitRes.length; j++ )
        			{
        				String[] splitValueRes = splitRes[j].split( "#" );
        				String varValueRes = splitValueRes[0];
        				String prefixValueRes = ""; if( splitValueRes.length > 2 ) prefixValueRes = splitValueRes[1] + "#";
        				String namespaceValueRes = null;
        				if( varKey.equalsIgnoreCase( varValueRes ) ) continue;
        				else namespaceValueRes = prefixTree.matchAndReplaceNamespace( prefixValueRes );
        				if( splitValueRes.length > 2 ) vars.put( varValueRes, namespaceValueRes + splitValueRes[2] );
        				else vars.put( varValueRes, namespaceValueRes + splitValueRes[1] );
        			}
        			
        			String resultInOrder = ""; 
        			Iterator<String> iterMap = vars.values().iterator();
        			while( iterMap.hasNext() )
        			{
        				resultInOrder += iterMap.next() + "\t";
        			}
        			context.write( new Text( resultInOrder ), new Text( "" ) );
        		}
        	}
        }
        else
        	context.write( key, new Text( sValue ) );		
	}
}
