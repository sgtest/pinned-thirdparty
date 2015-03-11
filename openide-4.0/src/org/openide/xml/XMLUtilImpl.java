/*
 *                 Sun Public License Notice
 * 
 * The contents of this file are subject to the Sun Public License
 * Version 1.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://www.sun.com/
 * 
 * The Original Code is NetBeans. The Initial Developer of the Original
 * Code is Sun Microsystems, Inc. Portions Copyright 1997-2004 Sun
 * Microsystems, Inc. All Rights Reserved.
 */

package org.openide.xml;

import java.io.*;
import java.util.*;
import java.lang.reflect.*;

import javax.xml.parsers.*;
/*
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.DocumentType;
 */

import org.xml.sax.*;
import org.w3c.dom.Document;

import org.openide.util.Lookup;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * This backend class for XMLUtil contains an implementation of writing.
 * @author  Petr Kuzel, Jesse Glick
 */
class XMLUtilImpl extends Object {

    private XMLUtilImpl() {}
    
    static void write(Document doc, OutputStream out, String encoding) throws IOException {
        // Cannot use JAXP.
        // 1. Indentation does not work - prints new elements on new lines at column one.
        // (Setting {http://xml.apache.org/xslt}indent-amount to '4' works in JDK 1.4 but
        // not in 1.5: BT #5064280.)
        // 2. Writing namespaces does not work when creating a new document.
        // Cf. some bugs in Xalan w.r.t. writing out namespaced attributes:
        // http://nagoya.apache.org/bugzilla/show_bug.cgi?id=26319
        // And information on status in JDK: BT #4981389
        /*
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            DocumentType dt = doc.getDoctype();
            if (dt != null) {
                String pub = dt.getPublicId();
                if (pub != null) {
                    t.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, pub);
                }
                t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, dt.getSystemId());
            }
            t.setOutputProperty(OutputKeys.ENCODING, encoding);
            t.setOutputProperty(OutputKeys.INDENT, "yes"); // NOI18N
            Source source = new DOMSource(doc);
            Result result = new StreamResult(out);
            t.transform(source, result);
        } catch (Exception e) {
            throw (IOException)new IOException(e.toString()).initCause(e);
        } catch (TransformerFactoryConfigurationError e) {
            throw (IOException)new IOException(e.toString()).initCause(e);
        }
         */
        
        Class dock = doc.getClass();
                        
        // no implementation neutral write exist
        try {
            if (("com.sun.xml.tree.XmlDocument".equals(dock.getName())           //NOI18N
                    || "org.apache.crimson.tree.XmlDocument".equals(dock.getName()))  //NOI18N
                && !hasNamespaces(doc)) {
                
                // these DOM implementations are self writing
		Method write = dock.getDeclaredMethod("write", new Class[] {OutputStream.class});//NOI18N
                write.invoke(doc,new Object[] {out});            
                
            } else {
                
                // try apache's serialize package
                // using introspection because calling implementation
                // specific methods 
                // may change as they prove to be stable
                
                ClassLoader cl = dock.getClassLoader();
                Class serka = null;
                try {
                    serka = Class.forName("org.apache.xml.serialize.XMLSerializer", true, cl);   //NOI18N
                } catch (ClassNotFoundException cnfe) {
                    // Possible, try another loader
                }
                
                if (serka == null) {
                    cl = Thread.currentThread().getContextClassLoader();
                    try {
                        serka = Class.forName("org.apache.xml.serialize.XMLSerializer", true, cl);   //NOI18N
                    } catch (ClassNotFoundException cnfe) {
                        // Still possible, try yet another loader
                    }
                }
                
                if (serka == null) {
                    cl = (ClassLoader)Lookup.getDefault().lookup(ClassLoader.class);
                    if (cl == null) cl = XMLUtilImpl.class.getClassLoader();
                    // Now pass the exception, nowhere to fallback anyway
                    serka = Class.forName("org.apache.xml.serialize.XMLSerializer", true, cl);   //NOI18N
                }
                
                // Load the formatter from the same classloader
                Class forka =
                    Class.forName("org.apache.xml.serialize.OutputFormat", true, cl);    //NOI18N
                
                Object serin = serka.newInstance();                
                Object forin = forka.newInstance();

                // hopefully it could improve output readability
                
                Method setmet = null;
                
                setmet = forka.getMethod("setMethod", new Class[] {String.class}); //NOI18N                
                setmet.invoke(forin, new Object[] {"xml"});                        //NOI18N                
                
                setmet = forka.getMethod("setIndenting", new Class[] {Boolean.TYPE}); //NOI18N                
                setmet.invoke(forin, new Object[] {Boolean.TRUE});                    //NOI18N

                setmet = forka.getMethod("setLineWidth", new Class[] {Integer.TYPE}); //NOI18N                
                setmet.invoke(forin, new Object[] {new Integer(0)});                  //NOI18N                
                
                setmet = forka.getMethod("setLineSeparator", new Class[] {String.class});  //NOI18N
                setmet.invoke(forin, new String[] {System.getProperty("line.separator")}); // NOI18N

                Method init = serka.getMethod("setOutputByteStream", new Class[] {OutputStream.class});  //NOI18N
                init.invoke(serin, new Object[] {out});                                            

                Method setenc = forka.getMethod("setEncoding", new Class[] {String.class});  //NOI18N              
                setenc.invoke(forin, new Object[] {encoding} );
                
                Method setout = serka.getMethod("setOutputFormat", new Class[] {forka});     //NOI18N
                setout.invoke(serin, new Object[] {forin});                
                
                Method setnam = serka.getMethod("setNamespaces", new Class[] {Boolean.TYPE}); // NOI18N
                setnam.invoke(serin, new Object[] {Boolean.TRUE});
                
                Method asDOM = serka.getMethod("asDOMSerializer", new Class[0]);//NOI18N
                Object impl = asDOM.invoke(serin, new Object[0]);

                Method serialize = impl.getClass().getMethod("serialize", new Class[] {Document.class}); //NOI18N
                serialize.invoke(impl, new Object[] {doc});
                  
            }
            
        } catch (IllegalAccessException ex) {
            handleImplementationException(ex);
        } catch (InstantiationException ex) {
            handleImplementationException(ex);
        } catch (IllegalArgumentException ex) {
            handleImplementationException(ex);
        } catch (NoSuchMethodException ex) {
            handleImplementationException(ex);
        } catch (ClassNotFoundException ex) {
            handleImplementationException(ex);
        } catch (InvocationTargetException ex) {
            handleTargetException(ex);
        }
    
    }
    
    /** @see "#36294" */
    private static boolean hasNamespaces(Document doc) {
        NodeList l = doc.getElementsByTagName("*");
        for (int i = 0; i < l.getLength(); i++) {
            if (((Element)l.item(i)).getNamespaceURI() != null) {
                return true;
            }
        }
        return false;
    }
    
    
    /** TargetException handler */
    private static void handleTargetException(InvocationTargetException ex) throws IOException {
        Throwable t = ex.getTargetException();
        if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } 
    }
    
    private static void handleImplementationException(Exception ex) throws IOException {
        throw (IOException)new IOException("Unsupported DOM document implementation! " + ex).initCause(ex); // NOI18N
    }
    
}
