# waller

A Clojure library for using Ragtime to migrate an ArangoDB database. It's named
after the ragtime pianist Fats Waller. 

## Usage

This project assumes that you are familiar with the basics of ragtime. Please
see https://github.com/weavejester/ragtime for details.

At the center of the migration scheme is the migration DSL. It has the following
form.
```
{:db "a-database-name",
 :action [:create | :drop | :modify],
 :collection-name "a-collection-name",
 :aql "String of AQL"}
 ```
 
 You must provide the :db and :action keys. After that, the DSL provides the
 context for behavior.  :collection-name specifies which collection you want
 to create or delete.  :action will create, drop or execute aql (:modify). 
 
 ### Examples
 
 
 #### Creating a DB
 Say you want to create a database named *nsa-clone*,
 you would create a file named *2015-04-18-1-database.up.edn*. The contents
 would look like:
 
 ```
 { :db "nsa-clone",
   :action :create}
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



## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
