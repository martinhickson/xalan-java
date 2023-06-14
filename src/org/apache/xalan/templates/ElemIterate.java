/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the  "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.xalan.templates;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.xalan.transformer.TransformerImpl;
import org.apache.xalan.xslt.util.XslTransformErrorLocatorHelper;
import org.apache.xml.dtm.DTM;
import org.apache.xml.dtm.DTMIterator;
import org.apache.xml.dtm.DTMManager;
import org.apache.xml.utils.IntStack;
import org.apache.xml.utils.QName;
import org.apache.xpath.Expression;
import org.apache.xpath.ExpressionOwner;
import org.apache.xpath.VariableStack;
import org.apache.xpath.XPath;
import org.apache.xpath.XPathContext;
import org.apache.xpath.objects.XObject;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XSLT 3.0 xsl:iterate element.
 * 
   <xsl:iterate select = expression>
      <!-- Content: (xsl:param*, xsl:on-completion?, sequence-constructor) -->
   </xsl:iterate>
   
   <xsl:next-iteration>
      <!-- Content: (xsl:with-param*) -->
   </xsl:next-iteration>

   <xsl:break select? = expression>
      <!-- Content: sequence-constructor -->
   </xsl:break>

   <xsl:on-completion select? = expression>
      <!-- Content: sequence-constructor -->
   </xsl:on-completion>
         
   @author Mukul Gandhi <mukulg@apache.org>
 * 
 * @xsl.usage advanced
 */
/*
 * Implementation of the XSLT 3.0 xsl:iterate instruction.
 * 
 * An xsl:iterate instruction, functions like a loop, with following main features,
 * 1) To be able to process a sequence of items in order, similar to how xsl:for-each 
 *    instruction shall do such processing.
 * 2) An xsl:iterate instruction has ability to exit from the loop prior to the 
 *    exhaustion of the input sequence when particular condition(s) become true,
 *    using xsl:break instruction. xsl:break being the final instruction (if invoked) 
 *    processed by xsl:iterate, can also specify particular XSLT evaluation to be 
 *    done via xsl:break's "select" attribute or with XSLT contents within xsl:break. 
 * 3) With xsl:iterate instruction, the stylesheet author could also specify, 
 *    particular XSLT processing to be done via xsl:on-completion instruction,
 *    after input sequence is exhausted. xsl:on-completion instruction is not
 *    invoked, when xsl:iterate processing is exited via xsl:break instruction.
 *    xsl:on-completion instruction can specify certain XSLT processing to be done,
 *    via its "select" attribute or via XSLT contents within xsl:on-completion.
 * 4) xsl:iterate can also have optional xsl:param elements, to allow passing 
 *    certain values as arguments to body of xsl:iterate (upon initial invocation of 
 *    xsl:iterate, or via xsl:next-iteration element just before a new iteration shall 
 *    start).
 * 5) The effect of xsl:iterate may also be achieved by XSLT 1.0 compatible named 
 *    templates. But using xsl:iterate likely makes writing such XSLT processing 
 *    simpler. 
 */
public class ElemIterate extends ElemTemplateElement implements ExpressionOwner
{

     private static final long serialVersionUID = -2692900882677332482L;
     
     private static final String OTHER_ELEM = "OTHER_ELEM";
              
     private List<ParamWithparamData> fParamList = new ArrayList<ParamWithparamData>();
     
     private List<ParamWithparamData> fWithparamList = new ArrayList<ParamWithparamData>();

     /**
      * Construct an element representing xsl:iterate.
      */
     public ElemIterate() {}

     /**
      * The "select" expression.
      */
     protected Expression m_selectExpression = null;

     public void setSelect(XPath xpath)
     {
         m_selectExpression = xpath.getExpression();
     }

     /**
      * Get the "select" attribute.
      *
      * @return The XPath expression for the "select" attribute.
      */
     public Expression getSelect()
     {
         return m_selectExpression;
     }

     /**
      * @see ExpressionOwner#setExpression(Expression)
      */
     public void setExpression(Expression exp)
     {
         exp.exprSetParent(this);
         m_selectExpression = exp;
     }
     
     /**
      * @see ExpressionOwner#getExpression()
     */
     public Expression getExpression()
     {
         return m_selectExpression;
     }
     
     /**
     * This function is called after everything else has been recomposed, 
     * and allows the template to set remaining values that may be based 
     * on some other property that depends on recomposition.
     *
     * @throws TransformerException
     */
     public void compose(StylesheetRoot sroot) throws TransformerException {
         super.compose(sroot);

         java.util.Vector vnames = sroot.getComposeState().getVariableNames();

         if (m_selectExpression != null) {
             
             m_selectExpression.fixupVariables(vnames, sroot.getComposeState().
                                                                  getGlobalsSize());
         }
         else {
             m_selectExpression = getStylesheetRoot().m_selectDefault.
                                                               getExpression();
         }
      }
  
      /**
       * This after the template's children have been composed.
      */
      public void endCompose(StylesheetRoot sroot) throws TransformerException
      {    
          super.endCompose(sroot);
      }

      /**
       * Get an int constant identifying the type of element.
       * @see org.apache.xalan.templates.Constants
       *
       * @return The token ID for this element
       */
       public int getXSLToken()
       {
           return Constants.ELEMNAME_ITERATE;
       }

       /**
         * Return the node name.
         *
         * @return The element's name
       */
       public String getNodeName()
       {
          return Constants.ELEMNAME_ITERATE_STRING;
       }

       /**
        * Execute the xsl:iterate transformation.
        *
        * @param transformer non-null reference to the the current transform-time state.
        *
        * @throws TransformerException
       */
       public void execute(TransformerImpl transformer) throws TransformerException
       {
           transformSelectedNodes(transformer);
       }

       /**
       * @param transformer              non-null reference to the the current transform-time state.
       *
       * @throws TransformerException    Thrown in a variety of circumstances.
       * 
       * @xsl.usage advanced
       */
       public void transformSelectedNodes(TransformerImpl transformer) throws TransformerException {
    
           final XPathContext xctxtOriginal = transformer.getXPathContext();
        
           XPathContext xctxt = transformer.getXPathContext();
         
           final int sourceNode = xctxt.getCurrentNode();
           
           validateXslElemIterateChildElementsSequence(xctxt);
        
           DTMIterator sourceNodes = m_selectExpression.asIterator(xctxt, sourceNode);
        
           try {               
               xctxt.pushCurrentNode(DTM.NULL);

               IntStack currentNodes = xctxt.getCurrentNodeStack();

               xctxt.pushCurrentExpressionNode(DTM.NULL);

               IntStack currentExpressionNodes = xctxt.getCurrentExpressionNodeStack();

               xctxt.pushSAXLocatorNull();
               xctxt.pushContextNodeList(sourceNodes);
               transformer.pushElemTemplateElement(null);                              
           
               int docID = sourceNode & DTMManager.IDENT_DTM_DEFAULT;
               int child;
               
               ElemIterateOnCompletion xslOnCompletionTemplate = null;
               
               while ((child = sourceNodes.nextNode()) != DTM.NULL) {
                   currentNodes.setTop(child);
                   currentExpressionNodes.setTop(child);

                   if ((child & DTMManager.IDENT_DTM_DEFAULT) != docID)
                   {
                       docID = child & DTMManager.IDENT_DTM_DEFAULT;
                   }                                  
                   
                   for (ElemTemplateElement elemTemplate = this.m_firstChild; elemTemplate != null; 
                                                                          elemTemplate = elemTemplate.m_nextSibling) {
                       if ((elemTemplate instanceof ElemIterateOnCompletion) && 
                                                                        (xslOnCompletionTemplate == null)) {
                           xslOnCompletionTemplate = (ElemIterateOnCompletion)elemTemplate;     
                       }
                       else if (elemTemplate instanceof ElemIterateNextIteration) {
                           VariableStack varStack = xctxt.getVarStack();
                           for (int idx = 0; idx < fWithparamList.size(); idx++) {
                               ParamWithparamData withParamData = fWithparamList.get(idx);
                               XPath withParamSelectVal = withParamData.getSelectVal();                               
                               XObject evalResult = withParamSelectVal.execute(xctxt, child, this);
                               // update value of current xsl:next-iteration's current xsl:param 
                               // 'parameter'. when xsl:iterate's new iteration is entered, this
                               // parameter shall have this new value.
                               varStack.setLocalVariable(idx, evalResult);
                           }                           
                       }
                       
                       if (!((XslTransformErrorLocatorHelper.isXslIterateBreakEvaluated).booleanValue())) {
                           xctxt.setSAXLocator(elemTemplate);
                           transformer.setCurrentElement(elemTemplate);
                           elemTemplate.execute(transformer);
                       }
                       else {
                           break;    
                       }
                   }
                   
                   if ((XslTransformErrorLocatorHelper.isXslIterateBreakEvaluated).booleanValue()) {                       
                       break;   
                   }
               }
               
               if ((xslOnCompletionTemplate != null) && !(XslTransformErrorLocatorHelper.
                                                                                isXslIterateBreakEvaluated).booleanValue()) {
                  XslTransformErrorLocatorHelper.isXslIterateOnCompletionActive = Boolean.TRUE;
                  xctxt.setSAXLocator(xslOnCompletionTemplate);
                  transformer.setCurrentElement(xslOnCompletionTemplate);
                  xslOnCompletionTemplate.execute(transformer);
                  XslTransformErrorLocatorHelper.isXslIterateOnCompletionActive = Boolean.FALSE;
               }
               
               XslTransformErrorLocatorHelper.isXslIterateBreakEvaluated = Boolean.FALSE;
           }
           finally {
              xctxt.popSAXLocator();
              xctxt.popContextNodeList();
              transformer.popElemTemplateElement();
              xctxt.popCurrentExpressionNode();
              xctxt.popCurrentNode();
              sourceNodes.detach();
           }
        
           // restore the xpath context, to where it was before this xsl:iterate 
           // instruction began an evaluation.
           transformer.setXPathContext(xctxtOriginal);        
      }
      
       /*
        * The XSLT 3.0 spec specifies constraints, about what should be the order of XSLT elements 
        * xsl:param, xsl:on-completion and xsl:next-iteration within the xsl:iterate element. This 
        * method ensures that, these XSLT xsl:iterate element constraints are validated during 
        * an XSLT document transformation.  
        */
      private void validateXslElemIterateChildElementsSequence(XPathContext xctxt) 
                                                                       throws TransformerException {
          
          List<String> xslElemNamesList = new ArrayList<String>();
          
          for (ElemTemplateElement elemTemplate = this.m_firstChild; 
                                                             elemTemplate != null; 
                                                             elemTemplate = elemTemplate.m_nextSibling) {
              if (elemTemplate instanceof ElemParam) {                  
                  xslElemNamesList.add(Constants.ELEMNAME_PARAMVARIABLE_STRING);
              }
              else if (elemTemplate instanceof ElemIterateOnCompletion) {
                  xslElemNamesList.add(Constants.ELEMNAME_ITERATE_ONCOMPLETION_STRING);   
              }
              else if (elemTemplate instanceof ElemIterateNextIteration) {
                  xslElemNamesList.add(Constants.ELEMNAME_ITERATE_NEXTITERATION_STRING);   
              }
              else {
                  xslElemNamesList.add(OTHER_ELEM);
              }
          }
          
          // get index of specific item's first occurrence with the list object 'xslElemNamesList'.
          // if a particular kind of xdm item that is checked is not present within the list object 
          // 'xslElemNamesList', its index is returned as -1.
          int paramIdx = xslElemNamesList.indexOf(Constants.ELEMNAME_PARAMVARIABLE_STRING);
          int onCompletionIdx = xslElemNamesList.indexOf(Constants.ELEMNAME_ITERATE_ONCOMPLETION_STRING);
          int nextIterationIdx = xslElemNamesList.indexOf(Constants.ELEMNAME_ITERATE_NEXTITERATION_STRING);
          int otherElemIdx = xslElemNamesList.indexOf(OTHER_ELEM);
          
          if ((paramIdx != -1) && (onCompletionIdx != -1) && (paramIdx > onCompletionIdx)) {
              throw new TransformerException("XTSE0010 : an xsl:param element must occur before xsl:on-completion "
                                                                                            + "element.", xctxt.getSAXLocator());    
          }          
          else if ((paramIdx != -1) && (nextIterationIdx != -1) && (paramIdx > nextIterationIdx)) {
              throw new TransformerException("XTSE0010 : an xsl:param element must occur before xsl:next-iteration "
                                                                                            + "element.", xctxt.getSAXLocator());
          }
          else if ((paramIdx != -1) && (otherElemIdx != -1) && (paramIdx > otherElemIdx)) {
              throw new TransformerException("XTSE0010 : an xsl:param element must occur before any other element within "
                                                                                     + "xsl:iterate element.", xctxt.getSAXLocator());
          }
          else if ((paramIdx != -1) && (otherElemIdx != -1) && (onCompletionIdx != -1) && (paramIdx < otherElemIdx) && 
                                                                                                 (otherElemIdx < onCompletionIdx)) {
              throw new TransformerException("XTSE0010 : an xsl:on-completion element must be the first child element of xsl:iterate "
                                                                                 + "after the xsl:param elements.", xctxt.getSAXLocator());
          }
          else if ((onCompletionIdx != -1) && (nextIterationIdx != -1) && (onCompletionIdx > nextIterationIdx)) {
              throw new TransformerException("XTSE0010 : an xsl:on-completion element must occur before xsl:next-iteration "
                                                                                                   + "element.", xctxt.getSAXLocator());
          }
          else if ((nextIterationIdx != -1) && (nextIterationIdx != (xslElemNamesList.size() - 1))) {
              throw new TransformerException("XTSE3120 : an xsl:next-iteration element when present, must be the last instruction within "
                                                                                         + "an xsl:iterate loop.", xctxt.getSAXLocator());
          }
          
          // Validate the, xsl:iterate->xsl:param* and xsl:next-iteration->xsl:with-param* names, as per following XSLT 3.0 
          // spec requirements,
          // 1) All the xsl:param names must be unique.
          // 2) All the xsl:next-iteration->xsl:with-param names must be unique.  
          // 3) Value of name attribute of xsl:param's must be pair-wise equal to value of name attribute of 
          //    xsl:next-iteration->xsl:with-param.          
          if (paramIdx != -1) {
              for (ElemTemplateElement elemTemplate = this.m_firstChild; elemTemplate != null; 
                                                                                 elemTemplate = elemTemplate.m_nextSibling) {
                  if (elemTemplate instanceof ElemParam) {
                     ElemParam paramElem = (ElemParam)elemTemplate;
                     QName paramNameVal = paramElem.getName();
                     XPath paramSelectXPath = paramElem.getSelect();
                     ParamWithparamData paramWithparamDataObj = new ParamWithparamData();
                     paramWithparamDataObj.setNameVal(paramNameVal);
                     paramWithparamDataObj.setSelectVal(paramSelectXPath);
                     if (fParamList.contains(paramWithparamDataObj)) {
                         throw new TransformerException("XTSE0580 : the name of the xsl:param parameter '" + paramNameVal + "' "
                                                                                         + "is not unique.", xctxt.getSAXLocator());    
                     }
                     else {
                         fParamList.add(paramWithparamDataObj);    
                     }
                  } 
                  else if (elemTemplate instanceof ElemIterateNextIteration) {
                      ElemIterateNextIteration elemIterateNextIteration = (ElemIterateNextIteration)elemTemplate;
                      NodeList nextIterationChildNodes = elemIterateNextIteration.getChildNodes();
                      for (int idx = 0; idx < nextIterationChildNodes.getLength(); idx++) {
                          Node nextIterChild = nextIterationChildNodes.item(idx);
                          if (nextIterChild instanceof ElemWithParam) {
                              ElemWithParam withParamElem = (ElemWithParam)nextIterChild;
                              QName withParamNameVal = withParamElem.getName();
                              XPath withParamSelectXPath = withParamElem.getSelect();                              
                              ParamWithparamData paramWithparamDataObj = new ParamWithparamData();
                              paramWithparamDataObj.setNameVal(withParamNameVal);
                              paramWithparamDataObj.setSelectVal(withParamSelectXPath);
                              if (fWithparamList.contains(paramWithparamDataObj)) {
                                 throw new TransformerException("XTSE0670 : duplicate xsl:with-param parameter name '" + withParamNameVal + 
                                                                                                                      "'", xctxt.getSAXLocator());   
                              }
                              else {
                                 fWithparamList.add(paramWithparamDataObj);  
                              }
                          }
                      }
                  }                  
              }
              
              if (fParamList.size() != fWithparamList.size()) {
                  throw new TransformerException("XTSE0580 : within xsl:iterate, the number of xsl:param elements are not equal to "
                                                                 + "number of xsl:next-iteration's xsl:with-param elements.", xctxt.getSAXLocator());     
              }
              else {
                 for (int idx = 0; idx < fParamList.size(); idx ++) {
                     ParamWithparamData paramData = fParamList.get(idx);
                     ParamWithparamData withParamData = fWithparamList.get(idx);
                     if (!(paramData.getNameVal()).equals(withParamData.getNameVal())) {
                         throw new TransformerException("XTSE3130 : within xsl:iterate, xsl:param and xsl:with-param names at position " + 
                                                                                                (idx + 1) + " are not same.", xctxt.getSAXLocator());        
                     }
                 }
              }
          }
          
      }
      
      /*
       * An object of this class, stores information about, one xsl:param 
       * element or one xsl:next-iteration->xsl:with-param element, for a 
       * particular xsl:iterate instruction. 
       */
      class ParamWithparamData {
          
          public QName nameVal;
          
          public XPath selectVal;

          public QName getNameVal() {
              return nameVal;
          }

          public void setNameVal(QName nameVal) {
              this.nameVal = nameVal;
          }

          public XPath getSelectVal() {
              return selectVal;
          }

          public void setSelectVal(XPath selectVal) {
              this.selectVal = selectVal;
          }
          
          @Override
          public boolean equals(Object obj) {
              if (this == obj) {
                  return true;
              }
              if (obj == null || getClass() != obj.getClass()) {
                  return false;
              }
              ParamWithparamData paramWithparamData = (ParamWithparamData)obj;
              return nameVal.equals(paramWithparamData.getNameVal());
          }
          
      }
      
}