/**
 *
 * Criteria API for Magnolia CMS (http://www.openmindlab.com/lab/products/mgnlcriteria.html)
 * Copyright(C) 2009-2013, Openmind S.r.l. http://www.openmindonline.it
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.vpro.jcr.criteria.advanced.impl;

import nl.vpro.jcr.criteria.query.Criteria;
import nl.vpro.jcr.criteria.query.JCRQueryException;
import nl.vpro.jcr.criteria.query.xpath.utils.XPathTextUtils;

import java.util.function.IntSupplier;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Warning, provisional class, users should not use this directly.
 * @author fgiust
 * @version $Id$
 */
public final class QueryExecutorHelper {

    private static final Logger LOG = LoggerFactory.getLogger(QueryExecutorHelper.class);

    private static ThreadLocal<Boolean> executing = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private QueryExecutorHelper() {
        // don't instantiate
    }

    /**
     * Executes a jcr query.
     * @param stmt the statement of the jcr query
     * @param language the language of the jcr query
     * @param jcrSession the Session
     * @param maxResults maximun number of results to retrieve
     * @param offset the index of the first result to retrieve (0, 1, 2, ...)
     * @param spellCheckString the input string used for spell checking
     * @return the execution result
     */
    public static AdvancedResultImpl execute(
        String stmt,
        String language,
        IntSupplier queryCounter,
        Session jcrSession,
        int maxResults,
        int offset,
        String spellCheckString) {
        return execute(stmt, language, queryCounter, jcrSession, maxResults, offset, spellCheckString, false);
    }

    public static AdvancedResultImpl execute(
        String stmt,
        String language,
        Session jcrSession,
        int maxResults,
        int offset,
        String spellCheckString) {

        return execute(stmt, language, jcrSession, maxResults, offset, spellCheckString, false);
    }

    public static AdvancedResultImpl execute(
        String stmt,
        String language,
        Session jcrSession,
        int maxResults,
        int offset,
        String spellCheckString,
        boolean forcePagingWithDocumentOrder
        ) {
        return execute(stmt, language, () -> {
            throw new UnsupportedOperationException();

        }, jcrSession, maxResults, offset, spellCheckString, forcePagingWithDocumentOrder);
    }

    /**
     * Executes a jcr query.
     * @param stmt the statement of the jcr query
     * @param language the language of the jcr query
     * @param jcrSession the Session
     * @param maxResults maximun number of results to retrieve
     * @param offset the index of the first result to retrieve (0, 1, 2, ...)
     * @param spellCheckString the input string used for spell checking
     * @param forcePagingWithDocumentOrder see {@link Criteria#setForcePagingWithDocumentOrder(boolean)}
     * @return the execution result
     */
    @SuppressWarnings("deprecation")
    public static AdvancedResultImpl execute(
        String stmt,
        String language,
        IntSupplier queryCounter,
        Session jcrSession,
        int maxResults,
        int offset,
        String spellCheckString,
        boolean forcePagingWithDocumentOrder) {
        javax.jcr.query.QueryManager jcrQueryManager;

        try {
            jcrQueryManager = jcrSession.getWorkspace().getQueryManager();

            @SuppressWarnings("deprecation")
            Query query = jcrQueryManager.createQuery(stmt, language);


            if (!forcePagingWithDocumentOrder) {
                if (maxResults > 0) {
                    query.setLimit(maxResults);
                }

                if (offset > 0) {
                    query.setOffset(offset);
                }
            }

            int pageNumberStartingFromOne = 1;
            if (maxResults > 0 && offset > maxResults - 1) {
                pageNumberStartingFromOne = (offset / maxResults) + 1;
            }

            Query spellCheckerQuery = null;

            if (StringUtils.isNotBlank(spellCheckString)) {
                spellCheckerQuery = jcrQueryManager.createQuery(
                    "/jcr:root[rep:spellcheck('"
                        + XPathTextUtils.stringToJCRSearchExp(spellCheckString)
                        + "')]/(rep:spellcheck())",
                    Query.XPATH);
            }

            try {
                executing.set(Boolean.TRUE);
                return new AdvancedResultImpl(
                    query.execute(),
                    queryCounter,
                    maxResults,
                    pageNumberStartingFromOne,
                    stmt,
                    spellCheckerQuery,
                    forcePagingWithDocumentOrder,
                    offset);
            } finally {
                executing.set(Boolean.FALSE);
            }
        } catch (RepositoryException e) {
            JCRQueryException jqe = new JCRQueryException(stmt, e);
            LOG.error(jqe.getMessage());
            throw jqe;
        }

    }

    /**
     * Indicates if this helper class is executing a query
     */
    public static boolean isExecuting() {
        return executing.get();
    }

}
