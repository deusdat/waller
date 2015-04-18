# waller

A Clojure library for using Ragtime to migrate an ArangoDB database. It's named
after the ragtime pianist Fats Waller. While ArangoDB requires less migration 
efforts than a traditional SQL DB, you still need to efficiently create databases,
collections and execute arbitrary AQL. This library lets you do that.

## The Core Concepts
This project assumes that you are familiar with the basics of ragtime. Please
see https://github.com/weavejester/ragtime for details.

### EDN Based Migration Snippets

At the center of the migration scheme is the migration DSL. It has the following
form.

```
{:action [:create | :drop | :modify],
 :collection-name "a-collection-name",
 :aql "String of AQL"}
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
you would create a file named *2015-04-18-1-database.up.edn*. The contents
would look like:
 
```
{ :action :create}
```

The library will use the credentials supplied on the URL to create a default 
user as part of the database creation process. (A future enhancement will add
a key :users for use during the db creation process.
  
Now that your database exists, you'll want to have some collections.
 
#### Creating a Collection
To create a document collection just specify the :db, :collection-name and 
:action :create.
 
```
{:db "nsa-clone",
 :collection-name "emails",
 :action :create}
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

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
