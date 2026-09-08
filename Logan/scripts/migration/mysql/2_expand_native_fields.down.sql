-- Shrinking these columns could discard AppIds and log content already stored.
SIGNAL SQLSTATE '45000'
  SET MESSAGE_TEXT = 'Automatic rollback is disabled: verify stored lengths before shrinking native fields';
