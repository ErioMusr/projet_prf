# Order Management System

Un système de gestion de commandes basé sur **Scala** et **Akka** (framework d'acteurs concurrents). Ce projet implémente une architecture microservices distribuée utilisant les acteurs typés d'Akka pour gérer les commandes, l'inventaire et les paiements.

---

## 🎯 Fonctionnalités principales

### 1. **Mode Utilisateur**
- **Placer une commande** : Sélectionner un produit et une quantité
- **Payer une commande** : Effectuer un paiement pour une commande en attente
- **Vérifier le statut d'une commande** : Consulter l'état et les détails d'une commande
- **Consulter l'historique des paiements** : Voir tous les paiements effectués

### 2. **Mode Administrateur**
- **Afficher l'inventaire** : Consulter le stock disponible de tous les produits
- **Mettre à jour l'inventaire** : Ajouter ou modifier les quantités de produits
- **Voir toutes les commandes** : Consulter l'historique complet des commandes
- **Voir tous les paiements** : Consulter l'historique complet des paiements

---

## 🏗️ Architecture du projet

### Système d'Acteurs Akka

Le projet utilise trois acteurs principaux qui communiquent de manière asynchrone :

#### 1. **OrderActor** (Gestionnaire de Commandes)
- Gère le cycle de vie complet d'une commande
- Coordinates l'interaction entre l'inventaire et les paiements
- Statuts possibles : `INIT` → `PENDING_INVENTORY_CHECK` → `STOCK_RESERVED` → `PAYMENT_PENDING` → `PAYMENT_SUCCESSFUL` / `CANCELLED`

#### 2. **InventoryActor** (Gestionnaire d'Inventaire)
- Maintient l'état du stock pour tous les produits
- Valide la disponibilité des articles lors d'une commande
- Réserve le stock lors d'une commande confirmée
- Restaure le stock en cas d'annulation

#### 3. **PaymentActor** (Gestionnaire de Paiements)
- Traite les paiements pour les commandes
- Enregistre l'historique des transactions
- Valide les montants de paiement

### Flux d'une Commande

```
1. Utilisateur place une commande
   ↓
2. OrderActor vérifie l'inventaire (CheckInventoryAmount)
   ↓
3. InventoryActor réserve le stock si disponible
   ↓
4. OrderActor reçoit la confirmation de cet-la commande passe au statut "STOCK_RESERVED"
   ↓
5. Utilisateur effectue un paiement
   ↓
6. OrderActor envoie le paiement au PaymentActor
   ↓
7. PaymentActor traite et valide le paiement
   ↓
8. Commande complétée ou annulée
```

### Fichiers du Projet

```
src/main/scala/
├── Main.scala              # Point d'entrée et initialisation du système
├── Protocol.scala          # Définition de tous les messages (Commands et Responses)
├── ConsoleUI.scala         # Interface utilisateur (Mode Utilisateur et Admin)
├── OrderActor.scala        # Gestion des commandes
├── InventoryActor.scala    # Gestion de l'inventaire
├── PaymentActor.scala      # Gestion des paiements
└── FileStore.scala         # Persistance des données en fichiers
```

---

## 📋 Prérequis

- **JDK 11+** (Java Development Kit)
- **Scala 2.13.12**
- **SBT 1.12.5+** (Scala Build Tool)

### Installation rapide

**Windows (avec Chocolatey):**
```powershell
choco install jdk11
choco install scala
choco install sbt
```

**macOS (avec Homebrew):**
```bash
brew install openjdk@11
brew install scala
brew install sbt
```

**Linux (Debian/Ubuntu):**
```bash
sudo apt-get install openjdk-11-jdk scala sbt
```

---

## 🚀 Lancer le Projet

### Étape 1 : Naviguer vers le répertoire du projet
```bash
cd c:\Users\Neptuniste\ -_-\Documents\projet_prf
```

### Étape 2 : Lancer l'application
```bash
sbt run
```

**Première exécution** : sbt téléchargera automatiquement toutes les dépendances. Cela peut prendre 2-5 minutes.

### Alternatives de lancement

**Compiler sans exécuter:**
```bash
sbt compile
```

**Exécuter le programme compilé:**
```bash
sbt "runMain Main"
```

**Nettoyer et relancer:**
```bash
sbt clean run
```

**Augmenter la mémoire allouée (si problèmes):**
```bash
sbt -J-Xmx4G -J-Xms2G run
```

---

## 💻 Guide d'Utilisation

### Menu Principal

Une fois lancé, le programme affiche le menu principal :

```
============================================================
ORDER MANAGEMENT SYSTEM
============================================================
1. User Mode    - Place orders and check status
2. Admin Mode   - Manage inventory and view all records
0. Exit
============================================================
```

### Mode Utilisateur (Option 1)

```
--- USER MODE ---
1. Place Order              # Créer une nouvelle commande
2. Pay for Order            # Effectuer un paiement
3. Check Order Status       # Vérifier l'état d'une commande
4. Check Payment Status     # Voir l'historique des paiements
0. Back to Main Menu
```

**Exemple de flux:**
1. Sélectionnez "Place Order"
2. ID du produit : `product-1`
3. Quantité : `5`
4. Gardez l'ID de commande généré (ex: `order-1712234567890`)
5. Retournez au menu et sélectionnez "Pay for Order"
6. Entrez l'ID de commande sauvegardé
7. Entrez le montant du paiement

### Mode Administrateur (Option 2)

```
--- ADMIN MODE ---
1. View Inventory           # Voir le stock de tous les produits
2. Update Inventory         # Modifier la quantité d'un produit
3. View All Orders          # Historique complet des commandes
4. View All Payments        # Historique complet des paiements
0. Back to Main Menu
```

**Exemple:**
1. Sélectionnez "Update Inventory"
2. ID du produit : `product-1`
3. Nouvelle quantité : `100`

---

## 📊 Structure des Données

### Commande (Order)
```
ID: order-TIMESTAMP
Produit: product-ID
Quantité: INT
Prix: DOUBLE
Montant Payé: DOUBLE
Statut: STRING (INIT, PENDING_INVENTORY_CHECK, STOCK_RESERVED, etc.)
Timestamp: LONG
```

### Paiement (Payment)
```
ID Commande: order-ID
Montant: DOUBLE
Statut: STRING (SUCCESSFUL, FAILED, PENDING)
Timestamp: LONG
```

### Inventaire (Inventory)
```
ID Produit: product-ID
Quantité Disponible: INT
```

---

## 🔄 Persistance des Données

Les données sont stockées dans des fichiers texte :

- **`order.txt`** : Historique des commandes
- **`payment.txt`** : Historique des paiements
- **`inventory.txt`** : État actuel de l'inventaire
- **`log.txt`** : Logs du système

Ces fichiers se trouvent à la racine du projet après la première exécution.

---

## ⚙️ Configuration

### Fichier `.sbtopts` (Mémoire JVM)

Le fichier `.sbtopts` à la racine configure la mémoire allouée à SBT :

```
-Xmx2G    # Mémoire maximale : 2 GB
-Xms1G    # Mémoire initiale : 1 GB
```

Modifiez ces valeurs si vous avez des problèmes de mémoire.

### Dépendances principales (build.sbt)

```scala
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20",
  "com.typesafe.akka" %% "akka-stream"      % "2.6.20",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.6.20" % Test
)
```

---

## 🛠️ Dépannage

### Erreur : "OutOfMemoryError"
**Solution :** Augmentez la mémoire dans `.sbtopts` ou exécutez :
```bash
sbt -J-Xmx4G run
```

### Erreur : "Akka system not initialized"
**Solution :** Assurez-vous que tous les acteurs sont correctement initialisés. Consultez les logs dans `log.txt`.

### Commande introuvable après annulation
**Solution :** Le système annule automatiquement les commandes impayées lors de l'arrêt. C'est un comportement normal.

---

## 📝 Statuts de Commande

| Statut | Description |
|--------|-------------|
| `INIT` | Commande créée, en attente de vérification d'inventaire |
| `PENDING_INVENTORY_CHECK` | Acteur inventaire vérifie la disponibilité |
| `STOCK_RESERVED` | Stock réservé, en attente de paiement |
| `PAYMENT_PENDING` | Paiement en cours de traitement |
| `PAYMENT_SUCCESSFUL` | Commande complétée avec succès |
| `CANCELLED` | Commande annulée |

---

## 🔐 Notes de Sécurité

- Les données sont stockées en fichiers texte simples (pas de chiffrement)
- Le système est conçu à titre éducatif
- En production, utiliser une véritable base de données
- Implémenter l'authentification et l'autorisation

---

## 📚 Ressources

- [Documentation Akka](https://akka.io/docs/)
- [Scala Documentation](https://docs.scala-lang.org/)
- [SBT Official Guide](https://www.scala-sbt.org/1.x/docs/)

---

## 📄 Licence

Projet éducatif - 2024

---

**Bon développement! 🚀**
