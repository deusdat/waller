# waller

A Clojure library for using Ragtime to migrate an ArangoDB database. It's named
after the ragtime pianist Fats Waller. While ArangoDB requires less migration 
efforts than a traditional SQL DB, you still need to efficiently create databases,
collections and execute arbitrary AQL. This library lets you do that.

## The Core Concepts
This project assumes that you are familiar with the basics of ragtime. Please
see https://github.com/weavejester/ragtime for details.

From the time of this README update, waller targets the 0.4.0 family over
the 0.3.0 family. If you need the older version, use tag 0.6.0. That tag
is no longer supported.

### EDN Based Migration Snippets

At the center of the migration scheme is the migration DSL. It has the following
form.

```
{:action [:create | :drop | :modify],
 :collection-name "a-collection-name",
 :aql "String of AQL",
 :fn ns.somwhere/no-arg-fn}
 ```
 
You must provide the :action keys. After that, the DSL provides the
context for behavior.  :collection-name specifies which collection you want
to create or delete.  :action will create, drop or execute aql (:modify). 

### Database URL

ArangoDB uses plain old HTTP to communicate. So you only have to have 
http://some-server:port when dealing with most drivers. In order to work with
ragtime, the protocol string is a little different. You must specify the 
server, credentials (optional), port and database to migrate like this 
 
```
arango://username:password@server:port/db-name.
```

If you connect to ArangoDB via HTTPS, then change the scheme to arangos.

```
arangos://username:password@server:port/db-name
```
### Migration Mechanics
When the migration starts, it will create a database at "/db-name" if it 
doesn't exists already. Inside that database, it will create a new collection
named "migrations". This collection tracks migrations executed over the 
life time of the application.

The credentials within the database URL must have rights to the create a
database and add a collection. These credentials will also go into the users
collection when creating the database.

### Programatic Useage
If you're like me, you use [Component](https://github.com/stuartsierra/component).
Fairly high up in your component chaining you'll want to migrate the database. 
This is fairly easy.

```
(ns user
  (require [ragtime.main :as rmain]))
  ...
  (rmain/migrate {:database "arango://tester1:pass@arangodb:8529/testing-db" 
                  :migrations "waller.files/migrations"})
```

## Examples

#### Creating a DB
 
Say you want to create a database named *nsa-clone*,
you would create a file named *2015-04-18-1-database.edn*. The contents
would look like:
 
```
{:up { :action :create},
 :down {:action :drop}}
```

The library will use the credentials supplied on the URL to create a default 
user as part of the database creation process. (A future enhancement will add
a key :users for use during the db creation process.
  
Now that your database exists, you'll want to have some collections.
 
#### Creating a Collection
To create a document collection just specify the :db, :collection-name and 
:action :create.
 
```
{:collection-name "emails",
 :action :create}
```

Let's name that migration 001-adds-emails-collection.edn.
```
{:up {:collection-name "emails", :action :create},
 :down {:action :drop :collection-name}}
```

#### Deleting a DB
To delete a database just specify :action :drop. Be **careful** with this feature. The migration system doesn't stop to make sure you really want to drop the database. Everything will go with the it.

```
{:action :drop}
```

#### Drop a Collection
To delete a collection specify the :action :drop :collection-name "a-collection".

```
{:action :drop,
 :collection-name "a-collection"}
```
#### Executing AQL
Let's say you want to add a new attribute to all of your docs as part of a migration. The below will do it for you. You can even have multi-line AQL strings. The system doesn't support variable bindings since you're going to make execute a static call. 

```
{:action :modify,
 :aql "FOR u in `testing-aql`
         UPDATE u WITH {status : 'new'} IN `testing-aql`"}
```

To undo adding an attribute, use a migration like so.

```
{:action :modify,
 :aql "FOR t in `testing-aql`
         LET t1 = UNSET(t, 'status')
         REPLACE t1 IN `testing-aql`"}
 ```
#### Basic Graphs
To create a graph, copy the EDN below. The collections in the :from and :to must exist prior to the migration for the graph. 

```
{:action :create,
 :graph {:name "my-graph",
         :edge-definitions [ {:collection "allname",
                              :from ["profile"],
                              :to ["vertex1"]
                              }]
        }
}
```

Dropping a graph is much easier. Collections remain; only the logical graph is destroyed.

```
{:action :drop,
 :graph "my-graph"}
```
### Manipulating Indexes
The base of the edn is the following map. The rest of the map varies by the
type of map.

```
{:action :create,
 :collection-name "name of collection",
 :index :hash | :cap | :skip | :geo | :text}
```

To create a hash index add to the above map

```
{:unique true | false,
 :fields ["paths" "of" "attributes"]}
```

To create a cap constraints index you must specify either a maximum number of
documents per collection, or a maximum number of bytes for a collection. If
you specify both values, Arango will evict documents based on the first met
criteria.

```
{:size 4,
 :byte-size 16384}
```

To create a skiplist add to the base map the following values.

```
{:unique true | false,
 :fields ["paths" "of" "attributes"],
 :sparse true | false}
```

To create a geo index, you only have to add fields vector

```
{:fields [geoJsonField] | [lat,long]}
```

A geo index field can either be one field that's vector of two elements
or two fields that comprise the lat long, in that order.

To create a full text index add the following to the base map.

```
{:fields ["attrA", "attrB"],
 :min-length 5}
```
:min-length defines the minimum length a word needs to be indexed. If leftout,
the server sets the default.

### Creating arbitrary AQL
While there are time where modifying a bulk collections is what you want, which
the :aql option supports, there are times where you'll need to access the 
environment to create the AQL. For example, you need to create documents in your
user table for your system accounts. You don't want to include their credentials
in the the source control.

That's where this feature comes in. The :fn key points to a function that takes
no arguments and returns an AQL statement.

```
{:action :modify
 :fn hms-api.data.migration/add-web-user!}
```

The function itself creates AQL in the following way.

```
(defn add-web-user! []
  (str "INSERT {groups: ['web'], username: '" 
       (env :webusername) "', password: '" 
       (env :webpassword) "'} IN accounts"))
```

The password store in the environment variable is pre-hashed.

## Limitations
There are three known limitaitons. The first is a migration to delete a database entirely. Since the collection tracking migrations is in the database being migrated, the database will always be created. Secondly, graphs are not fully supported. You may create and drop a graph, but you can't yet use Waller to modify it. Finally, the collections are currently created with their default settings. If enough people want to have collection meta-configurations like log sizes, we'll add it.

## Road Map
Add user support. Add migration down support for indexes.

## License
Distributed under the Eclipse Public License, the same as Clojure.

Copyright © 2015 DeusDat Solutions.

