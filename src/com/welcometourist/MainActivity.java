package com.welcometourist;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.Toast;
 

public class MainActivity extends Activity {
	public static int editText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
       // addKeyListener();
    }
}

 /***   public void addKeyListener() {
    	
    	 
    	// get edittext component
    final EditText edittext = (EditText) findViewById(R.id.editText);
     
    	// add a keylistener to keep track user input
    	edittext.setOnKeyListener(new OnKeyListener() {
    	public boolean onKey(View v, int keyCode, KeyEvent event) {
     
    		// if keydown and "enter" is pressed
    		if ((event.getAction() == KeyEvent.ACTION_DOWN)
    			&& (keyCode == KeyEvent.KEYCODE_ENTER)) {
     
    			// display a floating message
    			Toast.makeText(MainActivity.this,
    		//	final EditText = (EditText) findViewById(R.id.editText)
    			edittext.getText(), Toast.LENGTH_LONG).show();
    			return true;
     
    		} else if ((event.getAction() == KeyEvent.ACTION_DOWN)
    			&& (keyCode == KeyEvent.KEYCODE_9)) {
     
    			// display a floating message
    			Toast.makeText(MainActivity.this,
    				"Number 9 is pressed!", Toast.LENGTH_LONG).show();
    			return true;
    		}
     
    		return false;
    	}
     });

    
}
} **/

// data_access.cpp
// John May, 5 November 2004

/*****************************************************************
* PerfTrack Version 1.0 (September 2005)
* 
* For information on PerfTrack, please contact John May
* (johnmay@llnl.gov) or Karen Karavanic (karavan@cs.pdx.edu).
* 
* See COPYRIGHT AND LICENSE information at the end of this file.
*
*****************************************************************/

#define USE_GROUP_BY 1

#include <qcheckbox.h>
#include <qcombobox.h>
#include <qlineedit.h>
#include <qmap.h>
#include <qmessagebox.h>
#include <qpair.h>
#include <qregexp.h>
#include <qsettings.h>
#include <qspinbox.h>
#include <q3sqlselectcursor.h>
#include <qsqlquery.h>
#include <qstring.h>
#include <qstringlist.h>
#include <qlist.h>

#include <qdatetime.h>
//Added by qt3to4:
#include <Q3ValueList>
// smithm 2008-6-25
// Added to get QSqlQuery::lastError to work
#include <QSqlError>

#include <unistd.h>

#include <iostream>

#include "data_access.h"
#include "host_connection.h"
#include "resource_type_and_name.h"
#include "result_table_cursor.h"

#include "data_access.moc"

// Text keys for storing host and database defaults
const QString APP_KEY = "/Perf Track GUI/";
const QString DB_NAME = "dbName";
const QString HOST_NAME = "hostName";
const QString USER_NAME = "userName";
const QString DB_TYPE = "dbType";
const QString REMOTE_HOST = "remoteHost";
const QString REMOTE_USER = "remoteUser";
const QString DB_PORT = "dbPort";

//! List of ports used by database types.  This is only
//! used for forwarding ports through ssh to access
//! databases on remote machines.  For local access,
//! Qt figures everything out.  This class may need to
//! be extended if we support more remote databases.
struct PortMapClass : public QMap<QString,int> {
	PortMapClass()
	{
		insert("QOCI8", 1521 );
		insert("QPSQL7", 5432 );
		insert("QPSQL", 5432);
		insert("QMYSQL3", 3306 );
	}
	// Look up the value, returning -1 if not found.
	int operator[]( QString key )
	{
		QMap<QString,int>::iterator loc;
		if( (loc = find( key ) ) != end() ) {
			return *loc;
		}
		else {
			return -1;
		}
	}
};

static PortMapClass portMap;

DataAccess::DataAccess( QObject * parent, const char * name )
	: QObject( parent, name ), resultsUsingMetrics( 0 )
	// smithm 2008-6-25
	// Removed curDB from intitialization list because it is no longer a pointer
{
}

DataAccess::~DataAccess()
{
	// Should be no need to close the database, and it can
	// cause hangs.
	
	// smithmtest 2008-7-20
    // This is a hack for MySQL
    // Look for each temp table in the list (case-insensitively) and
	// drop it if needed.
	// QString driver = curDb.driverName();
	// 	QStringList tables = curDb.tables();
	// 	
	// 	if ( driver == "QMYSQL3" || driver == "QMYSQL" ) {
	// 		if( !tables.grep( resourceTableName, FALSE ).isEmpty() )
	// 			dropTempTable( resourceTableName );
	// 		if( !tables.grep( addTableName, FALSE ).isEmpty() )
	// 			dropTempTable( addTableName );
	// 		if( !tables.grep( focusTableName, FALSE ).isEmpty() )
	// 			dropTempTable( focusTableName );
	// 		if( !tables.grep( metricTableName, FALSE ).isEmpty() )
	// 			dropTempTable( metricTableName );
	// 		if( !tables.grep( metricAddTableName, FALSE ).isEmpty() )
	// 			dropTempTable( metricAddTableName );
	// 		if( !tables.grep( resultTableName, FALSE ).isEmpty() )
	// 			dropTempTable( resultTableName );
	//     }
}

bool DataAccess::setupDBConnection()
{
	QString dbname;
	QString dbuname;
	QString password;
	QString host;
	QString dbtype;
	QString remotehost;
	QString remotehostuname;
	int port_to_forward;
	int user_port;
	QSettings settings;
	HostConnection * hostConn = NULL;
	bool saveDefaults = FALSE;

	// Get default settings, if available
	settings.setPath( "llnl.gov", "Perf Track GUI", QSettings::User );

	// Get database information and try to open a connection
	do {
		// Create dialog to request connection info
		DBConnectionDialog * dbcd = new DBConnectionDialog();

		// Set the default values, if available
		dbcd->dbNameLineEdit->setText(
				settings.readEntry( APP_KEY + DB_NAME ) );
		dbcd->userNameLineEdit->setText(
				settings.readEntry( APP_KEY + USER_NAME ) );
		dbcd->hostNameLineEdit->setText(
				settings.readEntry( APP_KEY + HOST_NAME,
				       "localhost" ) );
		dbcd->ext->hostNameLineEdit->setText(
				settings.readEntry( APP_KEY + REMOTE_HOST ) );
		dbcd->ext->userNameLineEdit->setText(
				settings.readEntry( APP_KEY + REMOTE_USER ) );
		dbcd->portSpinBox->setValue( 
				settings.readNumEntry( APP_KEY + DB_PORT, -1 )
				);

		// To set the connection type, see if the default value
		// is in the current list; if so, set that value as current.
		QString defaultDbType = settings.readEntry( APP_KEY + DB_TYPE );
		if( ! defaultDbType.isNull() ) {
			QComboBox * theBox = dbcd->dbTypeComboBox;
			for( int i = 0; i < theBox->count(); ++i ) {
				if( theBox->text( i ) == defaultDbType ) {
					theBox->setCurrentItem( i );
					break;
				}
			}
		}

		// If the first entry was successfully populated, set the
		// focus to the password entry, since it will always be empty
		if( ! dbcd->dbNameLineEdit->text().isEmpty() ) {
			dbcd->passwordLineEdit->setFocus();
		}

		// Pop up the dialog and get the settings if accepted
		if( dbcd->exec() == QDialog::Accepted ) {
			dbname = dbcd->dbNameLineEdit->text();
			dbuname = dbcd->userNameLineEdit->text();
			password = dbcd->passwordLineEdit->text();
			host = dbcd->hostNameLineEdit->text();
			dbtype = dbcd->dbTypeComboBox->currentText();
			remotehost = dbcd->ext->hostNameLineEdit->text();
			remotehostuname = dbcd->ext->userNameLineEdit->text();
			user_port = dbcd->portSpinBox->value();
			saveDefaults
				= dbcd->defaultSettingsCheckBox->isChecked();
			delete dbcd;
		} else {
			// User opted out
			delete dbcd;
			emit databaseConnected( false );
			return false;
		}

		// Try to create a connection
		// If the user specified a host name and user name,
		// attempt to log in to the remote machine.
		if( ! remotehost.isEmpty() && ! remotehostuname.isEmpty() ) {
			// Figure out what port to forward.  If user
			// said "default" we have to look up a value in 
			// a local table based on the db name.  If we
			// don't know the default, ask for help.
			port_to_forward = ( user_port < 0 )
				?  portMap[dbtype] : user_port;
			if( port_to_forward < 0 ) {
				QString message = "Don't know the default port "
					"for " + dbtype + ".  Either enter a "
					"value or use a local connection.";
				int whatNow = QMessageBox::warning( NULL,
					"Default port unknown",
					message, QMessageBox::Retry,
					QMessageBox::Abort );
				if( whatNow == QMessageBox::Abort ) {
					emit databaseConnected( false );
					return false;
				} else {
					continue;
				}
			}

			hostConn = new HostConnection( remotehost,
					remotehostuname, port_to_forward );
			if( hostConn == NULL || hostConn->connect() == false ) {
				QString message = "Failed to connect to "
					+ remotehost + " as " + remotehostuname
					+ ".  See terminal or console for "
					"details.";
				int whatNow = QMessageBox::warning( NULL,
					"Remote Host Connection Error",
					message, QMessageBox::Retry,
					QMessageBox::Abort );
				if( hostConn != NULL ) delete hostConn;
				if( whatNow == QMessageBox::Abort ) {
					emit databaseConnected( false );
					return false;
				} else {
					// Try again
					continue;
				}
			}
		}

		// Now try to log in to the database
		curDb = QSqlDatabase::addDatabase( dbtype );
		// smithm 2008-6-25
		// curDb no longer a pointer, changed -> to .
		curDb.setDatabaseName( dbname );
		curDb.setHostName( host );
		if( user_port >= 0 ) {
			// Set nondefault port number
			curDb.setPort( user_port );
		}

		if( !curDb.open( dbuname, password ) ) {
			QString message = "Failed to connect to ";
			message += dbname + " on host " + host
				+ " as user " + dbuname + ":\n" 
				+ curDb.lastError().text();
			int whatNow = QMessageBox::warning( NULL,
					"Database Connection Error",
					message, QMessageBox::Retry,
					QMessageBox::Abort );

			//QSqlDatabase::removeDatabase( curDb );

			// smithm 2008-7-5
			// void removeDatabase ( QSqlDatabase * db ) is no longer available
			// in Qt 4.  In Qt 4, attempting to remove the database generates a
			// warning that the connection is still in use and all queries will
			// cease to work.  When the user re-enters the correct information
			// the and the same database type is entered the new connection
			// will not be added.  Thus, queries will not work.  However,
			// if we do not remove the database on error and just have the user
			// enter new database information the Qt database library will
			// generate warning about a connection that is still in use, and
			// that it has removed the old connection, but queries will work.

			// smithm 2008-6-25
			// curDb is no longer a pointer
			//curDb = NULL;
			if( hostConn != NULL ) delete hostConn;
			if( whatNow == QMessageBox::Abort ) {
				emit databaseConnected( false );
				return false;
			} 
			// If we get here, user wants to retry;
			// loop around to try again
		} else if( saveDefaults ) {
			// Save defaults only if connection opened successfully
			settings.writeEntry( APP_KEY + DB_NAME, dbname );
			settings.writeEntry( APP_KEY + USER_NAME, dbuname );
			settings.writeEntry( APP_KEY + HOST_NAME, host );
			settings.writeEntry( APP_KEY + DB_TYPE, dbtype );
			settings.writeEntry( APP_KEY + REMOTE_HOST,
					remotehost );
			settings.writeEntry( APP_KEY + REMOTE_USER,
					remotehostuname );
			settings.writeEntry( APP_KEY + DB_PORT, user_port );
		}
	
	// smithm 2006-8-25
	// curDb is no longer a pointer, check to see if it is valid instead.
	//} while( curDb == NULL );
	} while( !curDb.isOpen() );

	// Shouldn't get here until we sucessfully open a connection;
	// failures return earlier
	
	// Set customization strings based on the DBMS we connected to
	initDBCustomizations();

	// Create temporary tables used to cache intermediate results.
	// Different databases handle temp tables differently.  
	// PostGresQL, MySQL, and SQLite automatically drop these tables
	// at the end of each session, so we have to recreate them here.
	// Oracle does not drop the table, but different sessions don't
	// see each other's data.  So we'll check here to see if our
	// tables already exist, and create them if not.  No need to
	// provide a session-specific name, since all DBMS's we've looked
	// at keep the data separate.  No need to drop the tables when
	// we're done either, since that is either done automatically,
	// or else the tables are preserved but truncated.
	resourceTableName = "resources_temp";
	addTableName = "adds_temp";
	focusTableName = "contexts_temp";
	metricTableName = "metrics_temp";
	metricAddTableName = "metric_adds_temp";
	resultTableName = "results_temp";

	// Get a list of current tables
	QStringList tables = curDb.tables();
    
	// Look for each table in the list (case-insensitively) and
	// create it if needed.
	if( tables.grep( resourceTableName, FALSE ).isEmpty() )
		createTempTable( resourceTableName, "name VARCHAR(255)" );
	if( tables.grep( resourceTableName, FALSE ).isEmpty() )
		createTempTable( addTableName, "name VARCHAR(255)" );
	if( tables.grep( resourceTableName, FALSE ).isEmpty() )
		createTempTable( focusTableName, "focus_id INTEGER" );
	if( tables.grep( resourceTableName, FALSE ).isEmpty() )
		createTempTable( metricTableName, "id INTEGER" );
	if( tables.grep( resourceTableName, FALSE ).isEmpty() )
		createTempTable( metricAddTableName, "id INTEGER" );
	if( tables.grep( resourceTableName, FALSE ).isEmpty() )
		createTempTable( resultTableName,
			"saved SMALLINT, "
//S sharma -- made changes to add 'chk' column to GUI for showing High, Low, Expected and Unknown values of performance result
//			"value FLOAT, units VARCHAR(255), "
			"value FLOAT, chk VARCHAR(4000), units VARCHAR(255), "
			"metric VARCHAR(255), label VARCHAR(4000), combined SMALLINT, start_time VARCHAR(256), end_time VARCHAR(256), result_id INTEGER " );

	emit databaseConnected( true );
	return true;
}

void DataAccess::initDBCustomizations()
{
	QString driver = curDb.driverName();

	// Database-specific customizations
	if( driver == "QOCI8" ) {
		ociOrderedHint = " /*+ ORDERED */ ";
		tempTableFlag = " GLOBAL TEMPORARY ";
		tempTableSuffix = " ON COMMIT PRESERVE ROWS ";
	} else if ( driver == "QPSQL7" || driver == "QPSQL") {
		// smithm 2008-7-2
		// Changed to setup the same database customizations for the QPSQL7
		// and QPSQL driver.
		tempTableFlag = " GLOBAL TEMPORARY ";
		tempTableSuffix = " ON COMMIT PRESERVE ROWS ";
	} else if ( driver == "QSQLITE" ) {
		tempTableFlag = " TEMPORARY ";
		tempTableSuffix = "";
	// smithm 2008-6-25
	// It appears this should be a comparision and not an assignment.
	//} else if ( driver = "QMYSQL3" ) {
	// smithm 2008-7-8
	// Changed to setup the same database customizations for the QMYSQL3
	// and QMYSQL driver.
    // smithmtest
    // don't create temporary tables in MySQL
	// switched back to temporary tables for now
	} else if ( driver == "QMYSQL3" || driver == "QMYSQL" ) {
		// MySQL Not tested yet!
		tempTableFlag = " TEMPORARY ";
		tempTableSuffix = " ";
	// smithm 2008-7-2
	// Set default customization for all other databases.
	} else {
		tempTableFlag = " TEMPORARY ";
		tempTableSuffix = "";
	}
	
	// Other DB specific customizations to be added here as needed
}

QStringList DataAccess::queryForStrings( QString queryText )
{
	QStringList results;

	// Prepare the query
	QSqlQuery query( curDb );
	query.setForwardOnly( true );	// optimize for simple traversal

	// Execute the query
        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) ) {
		qWarning( "Failed to execute %s\n", queryText.latin1() );
		return results;
	}

	// Walk through the items and put the names in a list
	while( query.next() )  {
		results += query.value(0).toString();
	}

	return results;
}

int DataAccess::queryForInt( QString queryText )
{
	// Prepare the query
	QSqlQuery query( curDb );
	query.setForwardOnly( true );	// optimize for simple traversal

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	// Execute the query
	if( ! query.exec( queryText ) ) {
		fprintf( stderr, "Query failed: %s\n", queryText.latin1() );
		return -1;
	}

	// Walk through the items and put the names in a list
	if( query.next() )  {
		return query.value(0).toInt();
	}

	return -1;
}

Q3ValueList<QStringList> DataAccess::getResourceTypes()
{
	Q3ValueList<QStringList> rt;

// This version gets all known resource types, even if they
// aren't part of any focus
	QStringList resourceTypes = queryForStrings(
			"SELECT DISTINCT type FROM resource_item" );
// This version gets only the resource types that are part of some
// focus, plus their ancestors.  It takes much longer
//	QStringList resourceTypes = queryForStrings(
//			"SELECT DISTINCT type FROM resource_item WHERE id IN "
//			"(SELECT DISTINCT resource_id FROM focus_has_resource "
//			"UNION SELECT DISTINCT aid FROM resource_has_ancestor)"
//		);
	if( resourceTypes.isEmpty() ) return rt;

// Ugly hack!  "metric" is not currently stored in a focus, but we can
// always use it in a query because it's stored with every performance
// result.  This hack needs to be carried through resource selection
// result counting, and result acquisition.
	resourceTypes += "metric";
	resourceTypes.removeDuplicates(); //Annoys me seeing metric twice
	return parseResourceTypes( resourceTypes );
}
	
Q3ValueList<QStringList> DataAccess::parseResourceTypes(QStringList fullResourceTypes )
{
	// We want a list of of lists of resource type strings.
	// The database will pass us a list of resource names,
	// which look like: grid, grid/machine, grid/machine/partion,
	// etc.  We will use the full path names, not just the end
	// names, since the full names are stored with the resource
	// descriptions.  To divide these into groups, we will sort
	// the list of strings we get, which will order them by
	// groups, with the least-specific names appearing first.
	// Whenever a name doesn't contain the preceding name, we
	// start a new group.  The list may include nonhierarchical
	// resource names.  These should work fine; they'll just
	// produce singleton string lists.
	
	fullResourceTypes.sort();

	Q3ValueList<QStringList> resourceChains;
	QString lastString;	// Null string will be contained by any string
	QStringList currentChain;
	QStringList::Iterator rtit;
	for( rtit = fullResourceTypes.begin(); rtit != fullResourceTypes.end();
			++rtit ) {

		// Does this string contain the last one?  If not, start
		// a new chain.
		if( ! (*rtit).contains( lastString ) ) {
			resourceChains += currentChain;
			currentChain.clear();
		}

		// Add current string to the chain.
		currentChain += (*rtit);
		lastString = (*rtit);
	}

	// Add the last chain, if nonempty
	if( ! currentChain.isEmpty() ) {
		resourceChains += currentChain;
	}

	return resourceChains;
}

void DataAccess::findAttributesByType( QString resourceType, QString filter,void * requester )
{
#ifdef DEBUG
	qWarning( "findAttributesByType looking for resources "
			"of type %s with filter %s\n",
			resourceType.latin1(), filter.latin1() );
#endif

	// Prepare the query (on the default db for now: FIX)
	QSqlQuery query;
	query.setForwardOnly( true );	// optimize for simple traversal

	QString queryText = "SELECT DISTINCT ra.name "
			"FROM resource_item ri, resource_attribute ra "
			"WHERE ri.id = ra.resource_id "
			"AND type = '" + resourceType + "' ";

	// inFocus determines whether we insist that attributes we're
	// looking for correspond to resources that appear in some focus
	if( false ) {
		queryText += "AND ri.id IN "
				"(SELECT resource_id FROM focus_has_resource) ";
//				"(SELECT resource_id FROM resource_ids_jmm) ";
	}
	
	if( ! filter.isEmpty() )
		queryText += " AND " + filter + " ";

	// Since we're not merging the results, we'll have the database sort
	queryText += "ORDER BY ra.name";

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	// Create a list of results and return it
	QStringList attributes;
	while( query.next() )  {
		attributes += query.value(0).toString();
	}

	if( attributes.count() > 0 ) {
		emit foundAttributesByType( resourceType, attributes,
				requester );
	}
}

void DataAccess::findResourcesByType( QString resourceType, QString filter,void * requester )
{
#ifdef DEBUG
	qWarning( "findResourcesByType looking for resources "
			"of type %s with filter %s\n",
			resourceType.latin1(), filter.latin1() );
#endif

	// Prepare the query (on the default db for now: FIX)
	QSqlQuery query;
	query.setForwardOnly( true );	// optimize for simple traversal

	QString queryText = "SELECT name, id "
			"FROM resource_item "
			"WHERE type = '" + resourceType + "' ";

	if( ! filter.isEmpty() )
		queryText += " AND " + filter;

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

#ifdef USE_OLD_TABLES
	// Create a merged list of names, using either the full name
	// or just the last part (after the final '/'), depending on
	// the compiled value of USE_FULL_RESOURCE_NAMES
	QMap<QString,QPair<QString,QString> > map = buildResultMap( query,
			! USE_FULL_RESOURCE_NAMES );

	if( map.count() > 0 ) {
		emit foundResourcesByType( resourceType, map, requester );
	}
#else
	QMap<QString,int> map = buildResultMap( query );
	if( map.count() > 0 ) {
		emit foundResourcesByType( resourceType, map, requester );
	}
#endif

}

void DataAccess::findAttributesByName( QString attribute, QString filter, SelectionListItem * parentListItem )
{
#ifdef DEBUG
	qWarning( "findAttributesByName looking for "
			"attributes with attribute %s with filter %s\n",
			attribute.latin1(), filter.latin1() );
#endif

		// Prepare the query (on the default db for now: FIX)
	QSqlQuery query;
	query.setForwardOnly( true );	// optimize for simple traversal

#ifdef USE_OLD_TABLES
	QString queryText = "SELECT value, resource_id "
			"FROM resource_attribute "
			"WHERE name = '" + attribute + "' ";

	if( ! filter.isEmpty() )
		queryText += " AND " + filter;

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	QMap<QString,QPair<QString,QString> > map
		= buildResultMap( query, false );

	if( map.count() > 0 ) {
		emit foundAttributesByName( attribute, map, parentListItem );
	}
#else
	QString queryText = "SELECT DISTINCT value "
			"FROM resource_attribute "
			"WHERE name = '" + attribute + "' ";

	if( ! filter.isEmpty() )
		queryText += " AND " + filter + " ";

	queryText += "ORDER BY value";

	QStringList list = queryForStrings( queryText );

	if( list.count() > 0 ) {
		emit foundAttributesByName( attribute, list, parentListItem );
	}
#endif
}

void DataAccess::findResourcesByParent( QString idList, QString filter, SelectionListItem * parentListItem )
{
#ifdef DEBUG
	qWarning( "findResourcesByParent looking for "
			"resources with parent %s with filter %s\n",
			parentValue.latin1(), filter.latin1() );
#endif

	// Prepare the query (on the default db for now: FIX)
	QSqlQuery query;
	query.setForwardOnly( true );	// optimize for simple traversal

	QString queryText = "SELECT ri.name, ri.id "
			"FROM resource_item ri WHERE ri.parent_id IN "
			"(" + idList + ")";

	if( ! filter.isEmpty() )
		queryText += " AND " + filter;

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	QMap<QString,QPair<QString,QString> > map = buildResultMap( query,
			! USE_FULL_RESOURCE_NAMES );

	// See if we got any data; if not, skip the next step
	if( map.count() == 0 ) return;

	// Now we need to look up the type of these resources.  WE
	// ASSUME IT'S THE SAME FOR ALL CHILDREN OF THESE PARENTS,
	// so we look it up once and send a single value, rather than
	// pairing it with each item we return.
	
	QString firstId = idList.section( QChar(','), 0, 0 );
	queryText = "SELECT DISTINCT type FROM resource_item "
		"WHERE parent_id = " + firstId;

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	if( ! query.next() ) {	// Get to the first record, if possible
		qWarning( "Failed to get resource type on successul query!" );
		return;
	}

	QString type = query.value(0).toString();

	emit foundResourcesByParent( type, map, parentListItem );

}

void DataAccess::findResourcesByParent( QString parentType, QString baseName, QString filter, SelectionListItem * parentListItem )
{
#ifdef DEBUG
	qWarning( "findResourcesByParent looking for "
			"resources with parent %s with filter %s\n",
			parentValue.latin1(), filter.latin1() );
#endif

	// Prepare the query (on the default db for now: FIX)
	QSqlQuery query;
	query.setForwardOnly( true );	// optimize for simple traversal

	QString queryText =
		"SELECT ric.name FROM resource_item ric, resource_item rip "
		"WHERE ric.parent_id = rip.id "
		"AND rip.type = '" + parentType + "' "
		"AND rip.name LIKE '%" + baseName + "'";

	if( ! filter.isEmpty() )
		queryText += " AND " + filter;

        //debug
        fprintf(stderr, "findResourcesByParent executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	QMap<QString,int> map = buildResultMap( query );

	// See if we got any data; if not, skip the next step
	if( map.count() == 0 ) return;

	// Now we need to look up the type of these resources.  WE
	// ASSUME IT'S THE SAME FOR ALL CHILDREN OF THESE PARENTS,
	// so we look it up once and send a single value, rather than
	// pairing it with each item we return.
	queryText =
		"SELECT DISTINCT ric.type "
		"FROM resource_item ric, resource_item rip "
		"WHERE ric.parent_id = rip.id "
		"AND rip.type = '" + parentType + "'";

        //debug
        fprintf(stderr, "findResourcesByParent executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	if( ! query.next() ) {	// Get to the first record, if possible
		qWarning( "Failed to get resource type on successul query!" );
		return;
	}

	QString type = query.value(0).toString();

	emit foundResourcesByParent( type, map, parentListItem );

}

// Used for version that refers to resources only by name
// and does not store individual names locally
void DataAccess::findAttributesByResourceName( QString name,SelectionListItem * sli )
{
#ifdef DEBUG
	qWarning( "findAttributesByResourceName looking for attributes "
			"with name %s\n", name.latin1() );
#endif

		// Prepare the query (on the default db for now: FIX)
	QSqlQuery query;
	query.setForwardOnly( true );	// optimize for simple traversal

	QString queryText = "SELECT ri.name, ra.name, ra.value "
		"FROM resource_attribute ra, resource_item ri "
		"WHERE ri.id = ra.resource_id "
		"AND ri.name LIKE '%" + name + "' ";

        //debug
        fprintf(stderr, "executing: %s\n", queryText.latin1());
	if( ! query.exec( queryText ) )
		return;

	// Return a map of lists of pairs.  The map key is the
	// resource name (there may be only one), and the corresponding
	// list contains the attributes and their values.
	AttrListMap map;
	AttrListMap::iterator map_it;

	// Add each row from the result to the list.
	while( query.next() ) {
		QString key = query.value(0).toString();
		// See if the list exists for this resource; create it if not
		if( ( map_it = map.find( key ) ) == map.end() ) {
		       map_it = map.insert( key,
				       Q3ValueList<QPair<QString,QString> >() );
		}
